package org.example.backtrackweb.sync;

import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.service.NumberSequenceService;
import org.example.backtrackweb.sync.service.DeviceService;
import org.example.backtrackweb.sync.service.NumberBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Receipt numbers are handed out in blocks, never overlap, and are never
 * reissued - the prefix tells which device used the number.
 */
class NumberBlockTest extends SyncTestBase {

    @Autowired
    private NumberBlockService numberBlockService;
    @Autowired
    private NumberSequenceService numberSequenceService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = account("numberowner", Role.OWNER);
    }

    @Test
    void twoDevicesNeverReceiveOverlappingNumbers() {
        DeviceService.Registration first = register("numberowner", "PC 1");
        DeviceService.Registration second = register("numberowner", "Tablet");
        var firstPrincipal = principalOf(first.deviceUuid(), owner);
        var secondPrincipal = principalOf(second.deviceUuid(), owner);

        NumberBlockService.NumberBlock a = numberBlockService.issue(firstPrincipal,
                deviceOf(first.deviceUuid()), 100);
        NumberBlockService.NumberBlock b = numberBlockService.issue(secondPrincipal,
                deviceOf(second.deviceUuid()), 100);
        NumberBlockService.NumberBlock a2 = numberBlockService.issue(firstPrincipal,
                deviceOf(first.deviceUuid()), 100);

        assertThat(a.firstValue() + 99).isEqualTo(a.lastValue());
        assertThat(b.firstValue()).isGreaterThan(a.lastValue());
        assertThat(a2.firstValue()).isGreaterThan(b.lastValue());

        assertThat(a.prefix()).isNotEqualTo(b.prefix());
        assertThat(a.firstNumber()).isEqualTo("S-" + a.prefix() + "-000001");
        assertThat(a2.firstNumber()).isEqualTo(NumberBlockService.format(a.prefix(), a2.firstValue()));
    }

    @Test
    void theWebPosNumberingIsUnchangedAndNeverOverlapsDeviceBlocks() {
        DeviceService.Registration registration = register("numberowner", "PC");
        var principal = principalOf(registration.deviceUuid(), owner);
        NumberBlockService.NumberBlock block = numberBlockService.issue(principal,
                deviceOf(registration.deviceUuid()), 5);

        // The counter must run inside a transaction, like it does for a real sale.
        String webNumber = new TransactionTemplate(transactionManager)
                .execute(status -> numberSequenceService.nextSaleNumber());

        // The block took 1..5, so the shop counter continues at 6: one counter,
        // one sequence of numbers, no overlap and no reuse.
        assertThat(webNumber).isEqualTo("S-000006");
        assertThat(block.lastValue()).isEqualTo(5);
    }

    @Test
    void theBlockSizeIsBoundedAndDefaultsToFifty() {
        DeviceService.Registration registration = register("numberowner", "PC");
        var principal = principalOf(registration.deviceUuid(), owner);

        assertThatThrownBy(() -> numberBlockService.issue(principal,
                deviceOf(registration.deviceUuid()), NumberBlockService.MAX_BLOCK_SIZE + 1))
                .isInstanceOf(BusinessException.class);

        assertThat(numberBlockService.issue(principal, deviceOf(registration.deviceUuid()), 0).count())
                .isEqualTo(50);
    }

    @Test
    void aDeviceCannotAskForANumberBlockBelongingToAnotherDevice() {
        DeviceService.Registration first = register("numberowner", "PC 1");
        DeviceService.Registration second = register("numberowner", "PC 2");
        var firstPrincipal = principalOf(first.deviceUuid(), owner);

        assertThatThrownBy(() -> numberBlockService.issue(firstPrincipal,
                deviceOf(second.deviceUuid()), 10))
                .isInstanceOf(BusinessException.class);
    }
}
