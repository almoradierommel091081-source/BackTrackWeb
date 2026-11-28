package org.example.backtrackweb.sync;

import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.sync.entity.SyncLeaseEvent;
import org.example.backtrackweb.sync.service.DeviceService;
import org.example.backtrackweb.sync.service.SyncLeaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Only one device writes at a time, a stale epoch is refused, and every transfer
 * of control is on the record.
 */
class LeaseAndHandoverTest extends SyncTestBase {

    @Autowired
    private SyncLeaseService leaseService;

    private User owner;
    private DeviceService.Registration pc;
    private DeviceService.Registration tablet;

    @BeforeEach
    void setUp() {
        owner = account("leaseowner", Role.OWNER);
        pc = register("leaseowner", "Front desk PC");
        tablet = register("leaseowner", "Tablet");
    }

    @Test
    void theFirstDeviceClaimsAndASecondCannot() {
        SyncLeaseService.LeaseStatus claimed = leaseService.claim(principalOf(pc.deviceUuid(), owner));

        assertThat(claimed.epoch()).isEqualTo(1);
        assertThat(claimed.activeDeviceUuid()).isEqualTo(pc.deviceUuid());

        assertThatThrownBy(() -> leaseService.claim(principalOf(tablet.deviceUuid(), owner)))
                .isInstanceOf(SyncConflictException.class)
                .hasMessageContaining("Front desk PC");
    }

    @Test
    void aStaleEpochIsRefused() {
        leaseService.claim(principalOf(pc.deviceUuid(), owner));
        var pcPrincipal = principalOf(pc.deviceUuid(), owner);

        leaseService.verifyFence(1L, pcPrincipal.deviceId(), 1);

        assertThatThrownBy(() -> leaseService.verifyFence(1L, pcPrincipal.deviceId(), 0))
                .isInstanceOf(SyncConflictException.class)
                .hasFieldOrPropertyWithValue("reason", "stale_epoch");
    }

    @Test
    void handoverMovesTheEpochAndIsAudited() {
        leaseService.claim(principalOf(pc.deviceUuid(), owner));

        SyncLeaseService.HandoverResult result = leaseService.handover(
                principalOf(pc.deviceUuid(), owner), tablet.deviceUuid());

        assertThat(result.epoch()).isEqualTo(2);
        assertThat(result.activeDeviceUuid()).isEqualTo(tablet.deviceUuid());

        // The old device is fenced out immediately.
        var pcPrincipal = principalOf(pc.deviceUuid(), owner);
        assertThatThrownBy(() -> leaseService.verifyFence(1L, pcPrincipal.deviceId(), 1))
                .isInstanceOf(SyncConflictException.class)
                .hasFieldOrPropertyWithValue("reason", "not_lease_holder");

        assertThat(leaseService.history())
                .extracting(SyncLeaseEvent::getEvent)
                .contains(SyncLeaseEvent.CLAIMED, SyncLeaseEvent.HANDOVER);
    }

    @Test
    void onlyTheHolderCanHandOver() {
        leaseService.claim(principalOf(pc.deviceUuid(), owner));

        assertThatThrownBy(() -> leaseService.handover(
                principalOf(tablet.deviceUuid(), owner), pc.deviceUuid()))
                .isInstanceOf(SyncConflictException.class)
                .hasFieldOrPropertyWithValue("reason", "not_lease_holder");
    }

    @Test
    void forceHandoverIsOwnerOnlyAndWarnsAboutUnsentSales() {
        leaseService.claim(principalOf(pc.deviceUuid(), owner));
        User employee = account("leaseclerk", Role.EMPLOYEE);
        DeviceService.Registration counter = register("leaseclerk", "Counter PC");

        assertThatThrownBy(() -> leaseService.forceHandover(
                principalOf(counter.deviceUuid(), employee), tablet.deviceUuid(), "lost"))
                .isInstanceOf(BusinessException.class);

        // An owner may force it even from a device that does not hold the lease.
        SyncLeaseService.HandoverResult forced = leaseService.forceHandover(
                principalOf(register("leaseowner", "Owner phone").deviceUuid(), owner),
                tablet.deviceUuid(), "front desk PC was lost");
        assertThat(forced.epoch()).isEqualTo(2);
        assertThat(forced.warning()).contains("lost the lease immediately").contains("never sent");

        // The lost device cannot write any more, even though its token still works.
        var pcPrincipal = principalOf(pc.deviceUuid(), owner);
        assertThatThrownBy(() -> leaseService.verifyFence(1L, pcPrincipal.deviceId(), 1))
                .isInstanceOf(SyncConflictException.class);

        assertThat(leaseService.history())
                .extracting(SyncLeaseEvent::getEvent)
                .contains(SyncLeaseEvent.FORCE_HANDOVER);
        assertThat(leaseService.history().stream()
                .filter(event -> SyncLeaseEvent.FORCE_HANDOVER.equals(event.getEvent()))
                .findFirst().orElseThrow().getNote())
                .contains("front desk PC was lost");
    }

    @Test
    void releasingTheLeaseLetsAnotherDeviceTakeIt() {
        leaseService.claim(principalOf(pc.deviceUuid(), owner));
        leaseService.release(principalOf(pc.deviceUuid(), owner));

        SyncLeaseService.LeaseStatus claimed = leaseService.claim(principalOf(tablet.deviceUuid(), owner));
        assertThat(claimed.activeDeviceUuid()).isEqualTo(tablet.deviceUuid());
    }
}
