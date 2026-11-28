package org.example.backtrackweb.sync;

import org.example.backtrackweb.dto.ProductForm;
import org.example.backtrackweb.dto.SaleForm;
import org.example.backtrackweb.dto.SaleItemForm;
import org.example.backtrackweb.entity.DiscrepancyStatus;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.service.ProductService;
import org.example.backtrackweb.service.SaleService;
import org.example.backtrackweb.sync.dto.SyncPayloads;
import org.example.backtrackweb.sync.service.DeviceService;
import org.example.backtrackweb.sync.service.SyncLeaseService;
import org.example.backtrackweb.sync.service.SyncPushService;
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
 * Commands from a device are applied once, master data edits with an old version
 * are refused, and an offline sale is never lost even when stock is short.
 */
class SyncPushTest extends SyncTestBase {

    @Autowired
    private SyncPushService pushService;
    @Autowired
    private SyncLeaseService leaseService;
    @Autowired
    private ProductService productService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private org.example.backtrackweb.sync.service.NumberBlockService numberBlockService;
    @Autowired
    private ObjectMapper objectMapper;

    private User owner;
    private DeviceService.Registration registration;
    private String deviceUuid;

    @BeforeEach
    void setUp() {
        owner = account("pushowner", Role.OWNER);
        registration = register("pushowner", "Front desk PC");
        deviceUuid = registration.deviceUuid();
        leaseService.claim(principalOf(deviceUuid, owner));
    }

    private SyncPayloads.Operation op(String type, Map<String, Object> payload) {
        return new SyncPayloads.Operation(UUID.randomUUID().toString(), type,
                objectMapper.valueToTree(payload));
    }

    private SyncPayloads.PushResponse push(SyncPayloads.Operation... operations) {
        return pushService.push(principalOf(deviceUuid, owner),
                new SyncPayloads.PushRequest(deviceUuid, 1L, List.of(operations)));
    }

    private Map<String, Object> productPayload(String uuid, String sku, String name, int stockDelta) {
        return Map.of(
                "uuid", uuid,
                "sku", sku,
                "name", name,
                "sellingPrice", 100.00,
                "costPrice", 40.00,
                "minStockLevel", 1,
                "stockDelta", stockDelta
        );
    }

    private Product serverProduct(String sku, int stock) {
        ProductForm form = new ProductForm();
        form.setSku(sku);
        form.setName("Server " + sku);
        form.setSellingPrice(new BigDecimal("100.00"));
        form.setCostPrice(new BigDecimal("40.00"));
        form.setMinStockLevel(1);
        form.setOpeningStock(stock);
        return productService.create(form, owner);
    }

    @Test
    void aCommandIsAppliedOnceEvenWhenItIsRetried() {
        String uuid = UUID.randomUUID().toString();
        SyncPayloads.Operation created = op("PRODUCT_CREATED",
                productPayload(uuid, "SYNC-A", "Cable", 5));

        SyncPayloads.PushResponse first = push(created);
        SyncPayloads.PushResponse retry = push(created);

        assertThat(first.results()).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo("applied"));
        assertThat(retry.results()).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo("duplicate"));

        assertThat(productRepository.findAll()).hasSize(1);
        assertThat(productRepository.findByUuidAndBusinessId(uuid, 1L)).isPresent()
                .get().satisfies(product -> {
                    assertThat(product.getSku()).isEqualTo("SYNC-A");
                    assertThat(product.getStockQuantity()).isEqualTo(5);
                });
        assertThat(syncOperationRepository.count()).isEqualTo(1);
    }

    @Test
    void anEditBuiltOnAnOldVersionIsRefused() {
        String uuid = UUID.randomUUID().toString();
        push(op("PRODUCT_CREATED", productPayload(uuid, "SYNC-B", "Adapter", 2)));

        Map<String, Object> stale = Map.of(
                "uuid", uuid, "baseVersion", 9, "sku", "SYNC-B", "name", "Renamed",
                "sellingPrice", 120.00, "costPrice", 40.00, "minStockLevel", 1
        );
        SyncPayloads.PushResponse refused = push(op("PRODUCT_UPDATED", stale));

        assertThat(refused.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("rejected");
            assertThat(result.code()).isEqualTo("version_conflict");
            assertThat(result.message()).contains("changed on the server");
        });

        // Recording the opening stock already moved the product on one version.
        long currentVersion = productRepository.findByUuidAndBusinessId(uuid, 1L).orElseThrow().getVersion();
        Map<String, Object> current = Map.of(
                "uuid", uuid, "baseVersion", currentVersion, "sku", "SYNC-B", "name", "Renamed",
                "sellingPrice", 120.00, "costPrice", 40.00, "minStockLevel", 1
        );
        SyncPayloads.PushResponse applied = push(op("PRODUCT_UPDATED", current));
        assertThat(applied.results()).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo("applied"));
        assertThat(productRepository.findByUuidAndBusinessId(uuid, 1L).orElseThrow().getName())
                .isEqualTo("Renamed");
    }

    @Test
    void anOfflineSaleWithAFabricatedReceiptNumberIsRefused() {
        Product product = serverProduct("SALE-0", 5);

        Map<String, Object> sale = Map.of(
                "saleUuid", UUID.randomUUID().toString(),
                "saleNumber", "S-" + deviceOf(deviceUuid).receiptPrefix() + "-000777",
                "amountPaid", 100.00,
                "items", List.of(Map.of("productUuid", product.getUuid(), "quantity", 1)));

        SyncPayloads.PushResponse response = push(op("SALE_CREATED", sale));

        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("rejected");
            assertThat(result.message()).contains("outside every block issued");
        });
        assertThat(saleRepository.count()).isZero();
    }

    @Test
    void anOfflineSaleThatOutsellsTheStockIsKeptAndFlagged() {
        Product product = serverProduct("SALE-1", 1);
        var block = numberBlockService.issue(principalOf(deviceUuid, owner), deviceOf(deviceUuid), 10);

        Map<String, Object> sale = Map.of(
                "saleUuid", UUID.randomUUID().toString(),
                "saleNumber", block.firstNumber(),
                "paymentMethod", "CASH",
                "discountAmount", 0,
                "amountPaid", 300.00,
                "items", List.of(Map.of("productUuid", product.getUuid(), "quantity", 3))
        );

        SyncPayloads.PushResponse response = push(op("SALE_CREATED", sale));

        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("applied");
            assertThat(result.message()).contains("flagged for review");
        });

        Sale saved = saleService.getDetailed(saleRepository.findAll().getFirst().getId());
        assertThat(saved.isStockConflict()).isTrue();
        assertThat(saved.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getQuantity()).isEqualTo(3));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(-2);
        assertThat(inventoryDiscrepancyRepository.countByBusinessIdAndStatus(1L, DiscrepancyStatus.OPEN))
                .isEqualTo(1);

        // The shop counter still cannot oversell through the normal form.
        SaleForm form = new SaleForm();
        form.setAmountPaid(new BigDecimal("100.00"));
        SaleItemForm line = new SaleItemForm();
        line.setProductId(product.getId());
        line.setQuantity(1);
        form.setItems(List.of(line));
        assertThatThrownBy(() -> saleService.createSale(form, owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not enough stock");
    }

    @Test
    void aStockDeltaThatGoesNegativeIsFlaggedToo() {
        Product product = serverProduct("STOCK-1", 2);

        SyncPayloads.PushResponse response = push(op("STOCK_DELTA",
                Map.of("productUuid", product.getUuid(), "delta", -5, "reason", "Damaged in transit")));

        assertThat(response.results()).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo("applied"));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(-3);
        assertThat(inventoryDiscrepancyRepository.count()).isEqualTo(1);
    }

    @Test
    void aPushWithoutTheLeaseIsRefused() {
        leaseService.release(principalOf(deviceUuid, owner));

        assertThatThrownBy(() -> push(op("PRODUCT_CREATED",
                productPayload(UUID.randomUUID().toString(), "SYNC-C", "Plug", 1))))
                .isInstanceOf(SyncConflictException.class)
                .hasFieldOrPropertyWithValue("reason", "no_lease");
    }

    @Test
    void anUnknownCommandIsReportedWithoutFailingTheBatch() {
        String uuid = UUID.randomUUID().toString();
        SyncPayloads.PushResponse response = push(
                op("SOMETHING_ELSE", Map.of("x", 1)),
                op("PRODUCT_CREATED", productPayload(uuid, "SYNC-D", "Switch", 1)));

        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).status()).isEqualTo("rejected");
        assertThat(response.results().get(1).status()).isEqualTo("applied");
    }
}
