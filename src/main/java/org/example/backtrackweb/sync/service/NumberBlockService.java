package org.example.backtrackweb.sync.service;

import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.service.NumberSequenceService;
import org.example.backtrackweb.sync.entity.SyncDevice;
import org.example.backtrackweb.sync.entity.SyncNumberLease;
import org.example.backtrackweb.sync.repository.SyncNumberLeaseRepository;
import org.example.backtrackweb.sync.security.DevicePrincipal;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Hands a device a block of receipt numbers it may use while offline.
 *
 * <p>The counter is advanced by the whole block at once. Because the counter is
 * locked while it is read and written, two devices asking at the same time get
 * two different blocks, and a number that has been issued is never issued
 * again - even if the device is lost with numbers still unused.
 *
 * <p>Each device has its own prefix inside the business, so a receipt number
 * ({@code S-P7-000042}) identifies which device issued it.
 */
@Service
public class NumberBlockService {

    public static final int MAX_BLOCK_SIZE = 500;
    private static final Duration BLOCK_LIFETIME = Duration.ofDays(30);

    private final SyncNumberLeaseRepository leaseRepository;
    private final NumberSequenceService numberSequenceService;

    public NumberBlockService(SyncNumberLeaseRepository leaseRepository,
                              NumberSequenceService numberSequenceService) {
        this.leaseRepository = leaseRepository;
        this.numberSequenceService = numberSequenceService;
    }

    /** A granted block, ready for the device to format receipts from. */
    public record NumberBlock(String prefix, String firstNumber, String lastNumber,
                              long firstValue, long lastValue, int count, Instant expiresAt) {
    }

    @Transactional
    public NumberBlock issue(DevicePrincipal principal, SyncDevice device, int count) {
        Long businessId = TenantContext.require();
        if (principal.deviceId() == null || !device.getId().equals(principal.deviceId())) {
            throw new BusinessException("A number block can only be issued to the device asking for it.");
        }
        int size = count <= 0 ? 50 : count;
        if (size > MAX_BLOCK_SIZE) {
            throw new BusinessException("Ask for at most " + MAX_BLOCK_SIZE + " numbers at a time.");
        }

        String prefix = device.receiptPrefix();
        long[] range = numberSequenceService.reserveSaleNumbers(businessId, size);

        SyncNumberLease lease = new SyncNumberLease();
        lease.setBusinessId(businessId);
        lease.setDeviceId(device.getId());
        lease.setPrefix(prefix);
        lease.setFirstValue(range[0]);
        lease.setLastValue(range[1]);
        lease.setExpiresAt(Instant.now().plus(BLOCK_LIFETIME));
        lease = leaseRepository.save(lease);

        return new NumberBlock(prefix,
                format(prefix, range[0]), format(prefix, range[1]),
                range[0], range[1], size, lease.getExpiresAt());
    }

    public static String format(String prefix, long value) {
        return String.format("S-%s-%06d", prefix, value);
    }

    /**
     * Refuses a receipt number the server never issued to this device.
     *
     * <p>The device must use the prefix reserved for it and a number inside a
     * block it was granted. That is what keeps a receipt number from being
     * invented, reused, or borrowed from another device - a lost device cannot
     * later send sales with numbers that belong to someone else.
     */
    @Transactional(readOnly = true)
    public void assertIssuedTo(Long businessId, SyncDevice device, String saleNumber) {
        String prefix = device.receiptPrefix();
        String expectedStart = "S-" + prefix + "-";
        if (saleNumber == null || !saleNumber.startsWith(expectedStart)) {
            throw new BusinessException("Receipt number " + saleNumber
                    + " was not issued to this device. Use a number from a block requested with "
                    + "/api/v1/sync/number-block (format S-" + prefix + "-000001).");
        }
        long value;
        try {
            value = Long.parseLong(saleNumber.substring(expectedStart.length()));
        } catch (NumberFormatException ex) {
            throw new BusinessException("Receipt number " + saleNumber + " is not in the issued format.");
        }
        if (leaseRepository.countIssuedTo(businessId, device.getId(), prefix, value) == 0) {
            throw new BusinessException("Receipt number " + saleNumber
                    + " is outside every block issued to this device.\n"
                    + "Ask the server for a new block and continue from its first number.");
        }
    }
}
