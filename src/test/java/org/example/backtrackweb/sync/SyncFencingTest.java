package org.example.backtrackweb.sync;

import org.example.backtrackweb.dto.ProductForm;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.service.ProductService;
import org.example.backtrackweb.sync.dto.SyncPayloads;
import org.example.backtrackweb.sync.security.DevicePrincipal;
import org.example.backtrackweb.sync.service.DeviceService;
import org.example.backtrackweb.sync.service.SyncLeaseService;
import org.example.backtrackweb.sync.service.SyncPushService;
import org.example.backtrackweb.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each command is fenced individually, under a lock held until it commits.
 *
 * <p>These tests force the two situations that a batch-level check cannot
 * handle: a forced handover that arrives while a batch is running, and an edit
 * that loses an optimistic locking race. Both are arranged deterministically by
 * holding a row lock from the test thread, so the interleaving is fixed rather
 * than hoped for.
 */
class SyncFencingTest extends SyncTestBase {

    @Autowired
    private SyncPushService pushService;
    @Autowired
    private SyncLeaseService leaseService;
    @Autowired
    private ProductService productService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = account("fenceowner", Role.OWNER);
    }

    private SyncPayloads.Operation op(String type, Map<String, Object> payload) {
        return new SyncPayloads.Operation(UUID.randomUUID().toString(), type,
                objectMapper.valueToTree(payload));
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

    /**
     * Holds a write lock on one product row in its own transaction until the
     * latch is released. A command touching that row has to wait, which is what
     * makes the interleaving in these tests deterministic.
     *
     * @param edit when true the shop also changes the row before committing, so
     *             a command that read the old version loses the optimistic lock
     */
    private Thread lockProductRow(Long productId, boolean edit, CountDownLatch locked,
                                  CountDownLatch release) {
        Thread thread = new Thread(() -> {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> {
                TenantContext.set(1L);
                productRepository.findByIdForUpdateAndBusinessId(productId, 1L).orElseThrow();
                locked.countDown();
                await(release);
                if (edit) {
                    Product product = productRepository.findByIdAndBusinessId(productId, 1L)
                            .orElseThrow();
                    product.setName(product.getName() + " (edited by the shop)");
                    productRepository.save(product);
                }
            });
        });
        thread.setDaemon(true);
        return thread;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the test to reach its next step.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void aForcedHandoverDuringABatchStopsTheCommandsThatFollowIt() throws Exception {
        DeviceService.Registration pc = register("fenceowner", "Front desk PC");
        DeviceService.Registration tablet = register("fenceowner", "Tablet");
        leaseService.claim(principalOf(pc.deviceUuid(), owner));
        DevicePrincipal pcPrincipal = principalOf(pc.deviceUuid(), owner);
        DevicePrincipal tabletPrincipal = principalOf(tablet.deviceUuid(), owner);

        Product held = serverProduct("FENCE-0", 10);
        String secondUuid = UUID.randomUUID().toString();
        String thirdUuid = UUID.randomUUID().toString();

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread locker = lockProductRow(held.getId(), false, locked, release);
        locker.start();
        await(locked);

        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            Future<SyncPayloads.PushResponse> batch = pool.submit(() -> TenantContext.callAs(1L,
                    () -> pushService.push(pcPrincipal, new SyncPayloads.PushRequest(pc.deviceUuid(), 1L,
                            List.of(
                                    op("STOCK_DELTA", Map.of("productUuid", held.getUuid(),
                                            "delta", -1, "reason", "Reserved at the counter")),
                                    op("PRODUCT_CREATED", productPayload(secondUuid, "FENCE-1", "Second", 1)),
                                    op("PRODUCT_CREATED", productPayload(thirdUuid, "FENCE-2", "Third", 1)))))));

            // The first command has taken the lease row and is now waiting for the
            // locked product, so it holds the fence while it is in flight.
            Thread.sleep(500);

            Future<SyncLeaseService.HandoverResult> handover = pool.submit(() -> TenantContext.callAs(1L,
                    () -> leaseService.forceHandover(tabletPrincipal, tablet.deviceUuid(),
                            "front desk PC was lost")));

            // The handover cannot overtake a command that already holds the lease.
            Thread.sleep(500);
            assertThat(handover.isDone())
                    .as("a forced handover waits for the command that is in flight")
                    .isFalse();

            release.countDown();

            SyncPayloads.PushResponse response = batch.get(30, TimeUnit.SECONDS);
            SyncLeaseService.HandoverResult moved = handover.get(30, TimeUnit.SECONDS);
            locker.join(10_000);

            // The command that was already running finished under its own epoch.
            assertThat(response.results()).hasSize(3);
            assertThat(response.results().get(0).status())
                    .as("a command that already held the fence is allowed to finish")
                    .isEqualTo("applied");
            // Everything after the handover is fenced out: the device is no longer
            // the holder, so its old epoch authorises nothing.
            assertThat(response.results().get(1).status()).isEqualTo("rejected");
            assertThat(response.results().get(1).code()).isEqualTo("not_lease_holder");
            assertThat(response.results().get(2).status()).isEqualTo("rejected");
            assertThat(response.results().get(2).code()).isEqualTo("not_lease_holder");

            // Nothing from the commands after the handover reached the database.
            assertThat(productRepository.findByUuidAndBusinessId(secondUuid, 1L)).isEmpty();
            assertThat(productRepository.findByUuidAndBusinessId(thirdUuid, 1L)).isEmpty();
            assertThat(syncOperationRepository.count()).isEqualTo(1);
            assertThat(productRepository.findById(held.getId()).orElseThrow().getStockQuantity())
                    .isEqualTo(9);

            // The lease moved on exactly once, to the tablet.
            assertThat(moved.epoch()).isEqualTo(2);
            assertThat(leaseService.status().activeDeviceUuid()).isEqualTo(tablet.deviceUuid());
        } finally {
            pool.shutdownNow();
            release.countDown();
            locker.join(5_000);
        }
    }

    @Test
    void aCommandThatLosesAnOptimisticLockingRaceIsReportedAsAVersionConflict() throws Exception {
        Product product = serverProduct("LOCK-0", 5);
        long baseVersion = productRepository.findByUuidAndBusinessId(product.getUuid(), 1L)
                .orElseThrow().getVersion();

        DeviceService.Registration pc = register("fenceowner", "Front desk PC");
        leaseService.claim(principalOf(pc.deviceUuid(), owner));
        DevicePrincipal principal = principalOf(pc.deviceUuid(), owner);

        String laterUuid = UUID.randomUUID().toString();

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread locker = lockProductRow(product.getId(), true, locked, release);
        locker.start();
        await(locked);

        ExecutorService pool = Executors.newCachedThreadPool();
        Future<SyncPayloads.PushResponse> batch;
        try {
            batch = pool.submit(() -> TenantContext.callAs(1L,
                    () -> pushService.push(principal, new SyncPayloads.PushRequest(pc.deviceUuid(), 1L,
                            List.of(
                                    op("PRODUCT_UPDATED", Map.of(
                                            "uuid", product.getUuid(),
                                            "baseVersion", baseVersion,
                                            "sku", "LOCK-0",
                                            "name", "Renamed on the device",
                                            "sellingPrice", 120.00,
                                            "costPrice", 40.00,
                                            "minStockLevel", 1)),
                                    op("PRODUCT_CREATED", productPayload(laterUuid, "LOCK-1", "Later", 1)))))));

            // The update has read the product and is now waiting to write it; the
            // shop till commits first, so the version it was built on is gone.
            Thread.sleep(500);
        } finally {
            release.countDown();
        }

        SyncPayloads.PushResponse response = batch.get(30, TimeUnit.SECONDS);
        locker.join(10_000);
        pool.shutdownNow();

        assertThat(response.results().get(0).status()).isEqualTo("rejected");
        assertThat(response.results().get(0).code())
                .as("a lost optimistic lock is a per-command version conflict, not a server error")
                .isEqualTo("version_conflict");
        assertThat(response.results().get(0).message()).contains("changed by someone else");

        // The command that failed was rolled back on its own...
        assertThat(productRepository.findById(product.getId()).orElseThrow().getName())
                .isEqualTo("Server LOCK-0 (edited by the shop)");
        // ...and the command after it was still applied.
        assertThat(response.results().get(1).status()).isEqualTo("applied");
        assertThat(productRepository.findByUuidAndBusinessId(laterUuid, 1L)).isPresent();
    }
}
