package org.example.backtrackweb.sync.service;

import org.example.backtrackweb.sync.SyncConflictException;
import org.example.backtrackweb.sync.entity.SyncDevice;
import org.example.backtrackweb.sync.entity.SyncLease;
import org.example.backtrackweb.sync.entity.SyncLeaseEvent;
import org.example.backtrackweb.sync.repository.SyncDeviceRepository;
import org.example.backtrackweb.sync.repository.SyncLeaseEventRepository;
import org.example.backtrackweb.sync.repository.SyncLeaseRepository;
import org.example.backtrackweb.sync.security.DevicePrincipal;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * The single-writer lease.
 *
 * <p>Exactly one device may write to a business at a time. The lease is a row
 * with an epoch: granting it increments the epoch, and every write must name the
 * epoch it believes it holds. When the lease moves on, the previous device's
 * epoch becomes stale and its writes are refused instead of overwriting newer
 * data. That is what makes handover safe, and it is also why a lost device
 * cannot corrupt anything after an owner forces the lease away from it.
 *
 * <p>Every grant, release, handover and forced handover is written to the
 * handover history.
 */
@Service
public class SyncLeaseService {

    private final SyncLeaseRepository leaseRepository;
    private final SyncDeviceRepository deviceRepository;
    private final SyncLeaseEventRepository eventRepository;

    public SyncLeaseService(SyncLeaseRepository leaseRepository,
                            SyncDeviceRepository deviceRepository,
                            SyncLeaseEventRepository eventRepository) {
        this.leaseRepository = leaseRepository;
        this.deviceRepository = deviceRepository;
        this.eventRepository = eventRepository;
    }

    /** The lease as reported to a client. */
    public record LeaseStatus(long epoch, String activeDeviceUuid, String activeDeviceName,
                              Instant updatedAt) {
    }

    /** Result of a forced handover: the new epoch plus the warning to show. */
    public record HandoverResult(long epoch, String activeDeviceUuid, String warning) {
    }

    private Long tenant() {
        return TenantContext.require();
    }

    private SyncLease lockedLease(Long businessId) {
        return leaseRepository.findForUpdate(businessId).orElseGet(() -> {
            SyncLease lease = new SyncLease();
            lease.setBusinessId(businessId);
            lease.setEpoch(0);
            return leaseRepository.saveAndFlush(lease);
        });
    }

    @Transactional(readOnly = true)
    public long currentEpoch(Long businessId) {
        return leaseRepository.findById(businessId).map(SyncLease::getEpoch).orElse(0L);
    }

    @Transactional(readOnly = true)
    public LeaseStatus status() {
        Long businessId = tenant();
        SyncLease lease = leaseRepository.findById(businessId).orElse(null);
        if (lease == null) {
            return new LeaseStatus(0, null, null, null);
        }
        String uuid = null;
        String name = null;
        if (lease.getActiveDeviceId() != null) {
            Optional<SyncDevice> device = deviceRepository.findById(lease.getActiveDeviceId())
                    .filter(d -> d.getBusinessId().equals(businessId));
            uuid = device.map(SyncDevice::getUuid).orElse(null);
            name = device.map(SyncDevice::getName).orElse(null);
        }
        return new LeaseStatus(lease.getEpoch(), uuid, name, lease.getUpdatedAt());
    }

    /**
     * Takes the lease for a device. Succeeds when it is free or already held by
     * the same device; otherwise the caller is told who holds it.
     */
    @Transactional
    public LeaseStatus claim(DevicePrincipal principal) {
        Long businessId = principal.businessId();
        SyncLease lease = lockedLease(businessId);
        if (lease.isHeldBy(principal.deviceId())) {
            return status();
        }
        if (lease.getActiveDeviceId() != null) {
            SyncDevice holder = deviceRepository.findById(lease.getActiveDeviceId()).orElse(null);
            String holderName = holder == null ? "another device" : holder.getName();
            throw new SyncConflictException("lease_held",
                    "The business is currently in use on " + holderName
                            + ". Ask that device to hand over, or ask the owner to force a handover.");
        }
        grant(lease, principal.deviceId(), principal.userId(), SyncLeaseEvent.CLAIMED,
                "Lease claimed by " + principal.username());
        return status();
    }

    @Transactional
    public LeaseStatus release(DevicePrincipal principal) {
        Long businessId = principal.businessId();
        SyncLease lease = lockedLease(businessId);
        if (lease.getActiveDeviceId() != null && lease.isHeldBy(principal.deviceId())) {
            release(lease, principal.userId(), SyncLeaseEvent.RELEASED,
                    "Released by " + principal.username());
        }
        return status();
    }

    /**
     * Normal handover: the current holder names another device of the same
     * business and gives control to it.
     */
    @Transactional
    public HandoverResult handover(DevicePrincipal principal, String targetDeviceUuid) {
        Long businessId = principal.businessId();
        SyncLease lease = lockedLease(businessId);
        if (!lease.isHeldBy(principal.deviceId())) {
            throw new SyncConflictException("not_lease_holder",
                    "Only the device that currently holds the lease can hand it over.");
        }
        SyncDevice target = requireUsableDevice(targetDeviceUuid, businessId);
        if (target.getId().equals(principal.deviceId())) {
            throw new BusinessException("That device already holds the lease.");
        }
        long previousDeviceId = lease.getActiveDeviceId();
        grant(lease, target.getId(), principal.userId(), SyncLeaseEvent.HANDOVER,
                "Handed over from device " + principal.deviceUuid() + " to " + target.getName());
        return new HandoverResult(lease.getEpoch(), target.getUuid(), null);
    }

    /**
     * Forced handover, used when a device is lost or broken. Only an owner may
     * do this. The previous device's lease is invalidated immediately - its
     * epoch is now stale - and the caller is warned that the old device may
     * still be holding sales that were never sent.
     */
    @Transactional
    public HandoverResult forceHandover(DevicePrincipal principal, String targetDeviceUuid, String reason) {
        if (!principal.isOwner()) {
            throw new BusinessException("Only the business owner can force a handover.");
        }
        Long businessId = principal.businessId();
        SyncLease lease = lockedLease(businessId);
        SyncDevice target = requireUsableDevice(targetDeviceUuid, businessId);
        if (target.getId().equals(lease.getActiveDeviceId())) {
            throw new BusinessException("That device already holds the lease.");
        }

        SyncDevice previous = lease.getActiveDeviceId() == null
                ? null
                : deviceRepository.findById(lease.getActiveDeviceId()).orElse(null);
        String previousName = previous == null ? "no device" : previous.getName();

        String note = "Forced handover to " + target.getName() + " by " + principal.username()
                + (reason == null || reason.isBlank() ? "" : ": " + reason.trim());
        grant(lease, target.getId(), principal.userId(), SyncLeaseEvent.FORCE_HANDOVER, note);

        // The old device keeps its token but its epoch is now behind the lease,
        // so every further write it attempts is refused.
        String warning = "The previous device (" + previousName + ") has lost the lease immediately. "
                + "If it is lost or stolen, any sale it recorded offline but never sent cannot be "
                + "recovered by the server; those receipts are still on that device only.";
        return new HandoverResult(lease.getEpoch(), target.getUuid(), warning);
    }

    /** Called when the holder is revoked, so the business is not left stuck. */
    @Transactional
    public void releaseIfHeldBy(Long deviceId, Long actorUserId, String event, String note) {
        Long businessId = tenant();
        SyncLease lease = lockedLease(businessId);
        if (lease.isHeldBy(deviceId)) {
            release(lease, actorUserId, event, note);
        }
    }

    /**
     * Fencing check for every write. A stale epoch means the device lost the
     * lease, and nothing it sends may be applied.
     *
     * <p>This is the cheap, unlocked form used to reject a batch early. It is a
     * snapshot read: it says nothing about whether the lease is still held by
     * the time the change is written. {@link #lockAndVerify} is what actually
     * protects a command.
     */
    @Transactional(readOnly = true)
    public void verifyFence(Long businessId, Long deviceId, long epoch) {
        verify(leaseRepository.findById(businessId).orElse(null), deviceId, epoch);
    }

    /**
     * Fencing check for <em>one</em> command, under a lock on the lease row.
     *
     * <p>The row stays locked until the caller's transaction commits, so a
     * command and a handover can never interleave: either the command commits
     * first and the handover starts from a consistent state, or the handover
     * commits first and this command sees the new epoch and is refused. A check
     * that did not hold the lock could pass and then commit after a forced
     * handover had already taken the lease away.
     *
     * <p>Must run inside the transaction that applies the command
     * ({@code MANDATORY}), otherwise the lock would be released immediately.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAndVerify(Long businessId, Long deviceId, long epoch) {
        verify(leaseRepository.findForUpdate(businessId).orElse(null), deviceId, epoch);
    }

    private void verify(SyncLease lease, Long deviceId, long epoch) {
        if (lease == null || lease.getActiveDeviceId() == null) {
            throw new SyncConflictException("no_lease",
                    "This device does not hold the lease. Claim it before sending changes.");
        }
        if (!lease.isHeldBy(deviceId)) {
            throw new SyncConflictException("not_lease_holder",
                    "Another device holds the lease now. Pull the latest data and claim it again.");
        }
        if (epoch != lease.getEpoch()) {
            throw new SyncConflictException("stale_epoch",
                    "This device is using lease epoch " + epoch + " but the business is at "
                            + lease.getEpoch() + ". Pull the latest data before sending more changes.");
        }
    }

    private SyncDevice requireUsableDevice(String deviceUuid, Long businessId) {
        if (deviceUuid == null || deviceUuid.isBlank()) {
            throw new BusinessException("Name the device that should take over.");
        }
        SyncDevice device = deviceRepository.findByUuidAndBusinessId(deviceUuid, businessId)
                .orElseThrow(() -> new BusinessException("Device not found."));
        if (!device.isUsable(Instant.now())) {
            throw new BusinessException("That device is revoked or its token expired, so it cannot take over.");
        }
        return device;
    }

    private void grant(SyncLease lease, Long deviceId, Long actorUserId, String event, String note) {
        long previousDeviceId = lease.getActiveDeviceId() == null ? 0 : lease.getActiveDeviceId();
        lease.setEpoch(lease.getEpoch() + 1);
        lease.setActiveDeviceId(deviceId);
        lease.setUpdatedAt(Instant.now());
        lease.setUpdatedBy(actorUserId);
        leaseRepository.save(lease);

        deviceRepository.findById(deviceId).ifPresent(device -> {
            device.setLeaseEpoch(lease.getEpoch());
            deviceRepository.save(device);
        });

        SyncLeaseEvent record = new SyncLeaseEvent();
        record.setBusinessId(lease.getBusinessId());
        record.setEpoch(lease.getEpoch());
        record.setEvent(event);
        record.setDeviceId(deviceId);
        record.setPreviousDeviceId(previousDeviceId == 0 ? null : previousDeviceId);
        record.setActorUserId(actorUserId);
        record.setNote(note);
        eventRepository.save(record);
    }

    private void release(SyncLease lease, Long actorUserId, String event, String note) {
        long previousDeviceId = lease.getActiveDeviceId() == null ? 0 : lease.getActiveDeviceId();
        lease.setEpoch(lease.getEpoch() + 1);
        lease.setActiveDeviceId(null);
        lease.setUpdatedAt(Instant.now());
        lease.setUpdatedBy(actorUserId);
        leaseRepository.save(lease);

        SyncLeaseEvent record = new SyncLeaseEvent();
        record.setBusinessId(lease.getBusinessId());
        record.setEpoch(lease.getEpoch());
        record.setEvent(event);
        record.setPreviousDeviceId(previousDeviceId == 0 ? null : previousDeviceId);
        record.setActorUserId(actorUserId);
        record.setNote(note);
        eventRepository.save(record);
    }

    @Transactional(readOnly = true)
    public java.util.List<SyncLeaseEvent> history() {
        return eventRepository.findByBusinessIdOrderByCreatedAtDesc(tenant());
    }
}
