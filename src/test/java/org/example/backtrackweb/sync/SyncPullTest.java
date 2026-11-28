package org.example.backtrackweb.sync;

import org.example.backtrackweb.dto.ProductForm;
import org.example.backtrackweb.entity.DiscrepancyStatus;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.service.InventoryDiscrepancyService;
import org.example.backtrackweb.service.ProductService;
import org.example.backtrackweb.sync.dto.SyncPayloads;
import org.example.backtrackweb.sync.service.DeviceService;
import org.example.backtrackweb.sync.service.SyncLeaseService;
import org.example.backtrackweb.sync.service.SyncPullService;
import org.example.backtrackweb.sync.service.SyncPushService;
import org.example.backtrackweb.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The change feed is ordered, per business, and can be resumed from the last
 * sequence number a device saw. Discrepancies keep their history.
 */
class SyncPullTest extends SyncTestBase {

    @Autowired
    private SyncPullService pullService;
    @Autowired
    private SyncPushService pushService;
    @Autowired
    private SyncLeaseService leaseService;
    @Autowired
    private ProductService productService;
    @Autowired
    private InventoryDiscrepancyService discrepancyService;
    @Autowired
    private org.example.backtrackweb.sync.service.NumberBlockService numberBlockService;
    @Autowired
    private ObjectMapper objectMapper;

    private User owner;
    private DeviceService.Registration registration;

    @BeforeEach
    void setUp() {
        owner = account("pullowner", Role.OWNER);
        registration = register("pullowner", "Front desk PC");
        leaseService.claim(principalOf(registration.deviceUuid(), owner));
    }

    private SyncPayloads.Operation op(String type, Map<String, Object> payload) {
        return new SyncPayloads.Operation(UUID.randomUUID().toString(), type,
                objectMapper.valueToTree(payload));
    }

    private SyncPayloads.PushResponse push(User user, String deviceUuid,
                                           SyncPayloads.Operation... operations) {
        return pushService.push(principalOf(deviceUuid, user),
                new SyncPayloads.PushRequest(deviceUuid, 1L, List.of(operations)));
    }

    @Test
    void pullReturnsEveryChangeInOrderAndThenNothingMore() {
        SyncPayloads.PushResponse pushed = push(owner, registration.deviceUuid(),
                op("PRODUCT_CREATED", Map.of("uuid", UUID.randomUUID().toString(), "sku", "PULL-1",
                        "name", "Cable", "sellingPrice", 10.00, "costPrice", 5.00, "stockDelta", 3)),
                op("CUSTOMER_CREATED", Map.of("uuid", UUID.randomUUID().toString(), "name", "Ana")));

        assertThat(pushed.serverSeq()).isEqualTo(2);

        SyncPayloads.PullResponse all = pullService.pull(0, 100);
        assertThat(all.changes()).hasSize(2);
        assertThat(all.changes()).extracting(SyncPayloads.Change::serverSeq)
                .containsExactly(1L, 2L);
        assertThat(all.changes()).extracting(SyncPayloads.Change::entityType)
                .containsExactly("PRODUCT", "CUSTOMER");
        assertThat(all.changes().getFirst().data().get("sku").asString()).isEqualTo("PULL-1");
        assertThat(all.hasMore()).isFalse();
        assertThat(all.toSeq()).isEqualTo(2);

        SyncPayloads.PullResponse afterSync = pullService.pull(2, 100);
        assertThat(afterSync.changes()).isEmpty();
        assertThat(afterSync.latestSeq()).isEqualTo(2);
    }

    @Test
    void pullOnlyEverReturnsTheCallersBusiness() {
        push(owner, registration.deviceUuid(),
                op("PRODUCT_CREATED", Map.of("uuid", UUID.randomUUID().toString(), "sku", "MINE-1",
                        "name", "Mine", "sellingPrice", 10.00, "costPrice", 5.00)));

        Long otherBusiness = secondBusiness("Other Shop");
        User otherOwner = as(otherBusiness, () -> account("pullowner2", Role.OWNER));
        DeviceService.Registration otherDevice = as(otherBusiness,
                () -> register("pullowner2", "Other PC"));
        as(otherBusiness, () -> leaseService.claim(principalOf(otherDevice.deviceUuid(), otherOwner)));
        SyncPayloads.Operation theirProduct = op("PRODUCT_CREATED", Map.of(
                "uuid", UUID.randomUUID().toString(),
                "sku", "THEIRS-1",
                "name", "Theirs",
                "sellingPrice", 10.00,
                "costPrice", 5.00));
        as(otherBusiness, () -> pushService.push(principalOf(otherDevice.deviceUuid(), otherOwner),
                new SyncPayloads.PushRequest(otherDevice.deviceUuid(), 1L, List.of(theirProduct))));

        TenantContext.set(1L);
        assertThat(pullService.pull(0, 100).changes()).hasSize(1)
                .allSatisfy(change -> assertThat(change.data().get("sku").asString()).isEqualTo("MINE-1"));

        TenantContext.set(otherBusiness);
        assertThat(pullService.pull(0, 100).changes()).hasSize(1)
                .allSatisfy(change -> assertThat(change.data().get("sku").asString()).isEqualTo("THEIRS-1"));
    }

    @Test
    void aDiscrepancyIsKeptUntilAnOwnerResolvesIt() {
        ProductForm form = new ProductForm();
        form.setSku("WARN-1");
        form.setName("Battery");
        form.setSellingPrice(new BigDecimal("50.00"));
        form.setCostPrice(new BigDecimal("20.00"));
        form.setMinStockLevel(1);
        form.setOpeningStock(1);
        Product product = productService.create(form, owner);

        var block = numberBlockService.issue(principalOf(registration.deviceUuid(), owner),
                deviceOf(registration.deviceUuid()), 5);
        push(owner, registration.deviceUuid(),
                op("SALE_CREATED", Map.of(
                        "saleUuid", UUID.randomUUID().toString(),
                        "saleNumber", block.firstNumber(),
                        "amountPaid", 150.00,
                        "items", List.of(Map.of("productUuid", product.getUuid(), "quantity", 3)))));

        var open = discrepancyService.list(DiscrepancyStatus.OPEN);
        assertThat(open).hasSize(1);
        assertThat(open.getFirst().getResultingStock()).isEqualTo(-2);

        var resolved = discrepancyService.resolve(open.getFirst().getId(), "Counted after delivery",
                DiscrepancyStatus.RESOLVED, owner);

        assertThat(resolved.getStatus()).isEqualTo(DiscrepancyStatus.RESOLVED);
        assertThat(resolved.getResolvedBy().getUsername()).isEqualTo("pullowner");
        assertThat(resolved.getNote()).isEqualTo("Counted after delivery");
        // History is kept: the row is still there, only its status changed.
        assertThat(discrepancyService.list(null)).hasSize(1);

        assertThatThrownBy(() -> discrepancyService.resolve(resolved.getId(), "again",
                DiscrepancyStatus.RESOLVED, owner))
                .isInstanceOf(BusinessException.class);
    }
}
