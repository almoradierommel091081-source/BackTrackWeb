package org.example.backtrackweb.sync.service;

import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.exception.NotFoundException;
import org.example.backtrackweb.sync.SyncConflictException;
import org.example.backtrackweb.sync.dto.SyncPayloads;
import org.example.backtrackweb.sync.repository.SyncChangeLogRepository;
import org.example.backtrackweb.sync.security.DevicePrincipal;
import org.example.backtrackweb.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a batch of commands sent by a device.
 *
 * <p>The batch is only accepted from the device that holds the lease, using the
 * current epoch. Each command is then applied independently, so a command that
 * is rejected leaves the rest of the batch applied and is reported back with a
 * reason the client can act on.
 *
 * <p>The check at the top of the batch is only a fast refusal. Each command
 * re-checks the lease under a row lock inside its own transaction, so a forced
 * handover that happens half way through a batch stops the commands that follow
 * it instead of letting them commit under an epoch that no longer exists.
 *
 * <p>Because each command has its own transaction, the exceptions that matter
 * here are not only the ones thrown by the service methods: an optimistic lock
 * or a unique constraint usually fails <em>when the transaction commits</em>,
 * which happens inside {@link SyncOperationApplier#apply} as seen from here, and
 * is mapped to a result for that one command.
 */
@Service
public class SyncPushService {

    private static final Logger log = LoggerFactory.getLogger(SyncPushService.class);
    private static final int MAX_BATCH = 200;

    private final SyncLeaseService leaseService;
    private final SyncOperationApplier applier;
    private final SyncChangeLogRepository changeLogRepository;

    public SyncPushService(SyncLeaseService leaseService,
                           SyncOperationApplier applier,
                           SyncChangeLogRepository changeLogRepository) {
        this.leaseService = leaseService;
        this.applier = applier;
        this.changeLogRepository = changeLogRepository;
    }

    public SyncPayloads.PushResponse push(DevicePrincipal principal, SyncPayloads.PushRequest request) {
        Long businessId = TenantContext.require();
        if (request.deviceUuid() == null || !request.deviceUuid().equals(principal.deviceUuid())) {
            throw new SyncConflictException("device_mismatch",
                    "The batch was sent for a different device than the one that logged in.");
        }
        List<SyncPayloads.Operation> operations = request.operations() == null
                ? List.of() : request.operations();
        if (operations.size() > MAX_BATCH) {
            throw new BusinessException("Send at most " + MAX_BATCH + " commands per batch.");
        }

        long epoch = request.leaseEpoch() == null ? -1 : request.leaseEpoch();
        leaseService.verifyFence(businessId, principal.deviceId(), epoch);

        List<SyncPayloads.OperationResult> results = new ArrayList<>();
        for (SyncPayloads.Operation operation : operations) {
            if (operation == null || operation.opId() == null || operation.opId().isBlank()) {
                results.add(SyncPayloads.OperationResult.rejected("", "invalid_operation",
                        "Every command needs its own opId.", null));
                continue;
            }
            results.add(applyOne(principal, operation, epoch));
        }

        long serverSeq = changeLogRepository.maxServerSeq(businessId);
        return new SyncPayloads.PushResponse(serverSeq, epoch, results);
    }

    private SyncPayloads.OperationResult applyOne(DevicePrincipal principal,
                                                  SyncPayloads.Operation operation, long epoch) {
        try {
            return applier.apply(principal, operation, epoch);
        } catch (SyncConflictException ex) {
            return SyncPayloads.OperationResult.rejected(operation.opId(), ex.getReason(),
                    ex.getMessage(), null);
        } catch (OptimisticLockingFailureException ex) {
            // The row was changed by someone else between this command reading it
            // and writing it (a second cashier, or another push). Only this
            // command is affected; the rest of the batch has its own
            // transactions and is untouched.
            log.info("Sync command {} lost an optimistic locking race", operation.opId());
            return SyncPayloads.OperationResult.rejected(operation.opId(), "version_conflict",
                    "This record was changed by someone else at the same moment, so this command "
                            + "was not applied. Pull the latest data and re-apply your change.", null);
        } catch (NotFoundException ex) {
            return SyncPayloads.OperationResult.rejected(operation.opId(), "not_found",
                    ex.getMessage(), null);
        } catch (BusinessException ex) {
            return SyncPayloads.OperationResult.rejected(operation.opId(), "invalid", ex.getMessage(), null);
        } catch (DataIntegrityViolationException ex) {
            // Two identical pushes can reach the same constraint at the same
            // moment. If the other one committed, answer with its result; the
            // caller can then carry on instead of retrying blindly.
            SyncPayloads.OperationResult replayed = applier.replayIfApplied(principal, operation);
            if (replayed != null) {
                log.info("Command {} was applied by a concurrent identical push", operation.opId());
                return replayed;
            }
            log.info("Rejected sync command {} because it violated a constraint", operation.opId());
            return SyncPayloads.OperationResult.rejected(operation.opId(), "duplicate",
                    "That record already exists on the server (another receipt number or a duplicate "
                            + "SKU, for example). Pull the latest data and try again.", null);
        }
    }
}
