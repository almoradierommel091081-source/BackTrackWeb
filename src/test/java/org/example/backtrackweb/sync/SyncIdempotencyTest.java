package org.example.backtrackweb.sync;

import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.sync.dto.SyncPayloads;
import org.example.backtrackweb.sync.security.DevicePrincipal;
import org.example.backtrackweb.sync.service.DeviceService;
import org.example.backtrackweb.sync.service.SyncLeaseService;
import org.example.backtrackweb.sync.service.SyncPushService;
import org.example.backtrackweb.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An operation id means one command, once.
 *
 * <p>An exact retry is answered from the ledger and never re-applied. The same
 * id with different content, a different command type or from a different device
 * is refused. Ids are unique inside a business only, so two businesses can use
 * the same one without interfering.
 */
class SyncIdempotencyTest extends SyncTestBase {

    @Autowired
    private SyncPushService pushService;
    @Autowired
    private SyncLeaseService leaseService;
    @Autowired
    private ObjectMapper objectMapper;

    private User owner;
    private DeviceService.Registration pc;
    private DevicePrincipal principal;

    @BeforeEach
    void setUp() {
        owner = account("idemowner", Role.OWNER);
        pc = register("idemowner", "Front desk PC");
        leaseService.claim(principalOf(pc.deviceUuid(), owner));
        principal = principalOf(pc.deviceUuid(), owner);
    }

    private SyncPayloads.Operation op(String opId, String type, Object payload) {
        return new SyncPayloads.Operation(opId, type, objectMapper.valueToTree(payload));
    }

    private Map<String, Object> productPayload(String uuid, String sku, String name) {
        return Map.of(
                "uuid", uuid,
                "sku", sku,
                "name", name,
                "sellingPrice", 100.00,
                "costPrice", 40.00,
                "minStockLevel", 1,
                "stockDelta", 2
        );
    }

    private SyncPayloads.PushResponse push(DevicePrincipal device, String deviceUuid,
                                           SyncPayloads.Operation operation) {
        return pushService.push(device, new SyncPayloads.PushRequest(deviceUuid, 1L, List.of(operation)));
    }

    @Test
    void anExactRetryReturnsTheOriginalAnswerWithoutApplyingItAgain() {
        String opId = UUID.randomUUID().toString();
        SyncPayloads.Operation created = op(opId, "PRODUCT_CREATED",
                productPayload(UUID.randomUUID().toString(), "IDEM-1", "Cable"));

        SyncPayloads.PushResponse first = push(principal, pc.deviceUuid(), created);
        SyncPayloads.PushResponse retry = push(principal, pc.deviceUuid(), created);

        assertThat(first.results()).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo("applied"));
        assertThat(retry.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("duplicate");
            assertThat(result.code()).isNull();
            assertThat(result.message()).contains("original result");
            assertThat(result.data().get("uuid").asString())
                    .isEqualTo(first.results().getFirst().data().get("uuid").asString());
        });
        assertThat(productRepository.findAll()).hasSize(1);
        assertThat(syncOperationRepository.count()).isEqualTo(1);
    }

    @Test
    void aRetryIsRecognisedEvenWhenTheJsonKeysArriveInAnotherOrder() {
        String opId = UUID.randomUUID().toString();
        String uuid = UUID.randomUUID().toString();

        ObjectNode first = objectMapper.createObjectNode();
        first.put("uuid", uuid);
        first.put("sku", "IDEM-K");
        first.put("name", "Key order");
        first.put("sellingPrice", 100.00);
        first.put("costPrice", 40.00);
        first.put("minStockLevel", 1);
        first.put("stockDelta", 2);

        // The same command, with the properties written in a different order.
        ObjectNode reordered = objectMapper.createObjectNode();
        reordered.put("stockDelta", 2);
        reordered.put("minStockLevel", 1);
        reordered.put("name", "Key order");
        reordered.put("sku", "IDEM-K");
        reordered.put("costPrice", 40.00);
        reordered.put("uuid", uuid);
        reordered.put("sellingPrice", 100);

        SyncPayloads.PushResponse firstPush = push(principal, pc.deviceUuid(),
                new SyncPayloads.Operation(opId, "PRODUCT_CREATED", first));
        SyncPayloads.PushResponse secondPush = push(principal, pc.deviceUuid(),
                new SyncPayloads.Operation(opId, "PRODUCT_CREATED", reordered));

        assertThat(firstPush.results()).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo("applied"));
        assertThat(secondPush.results()).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo("duplicate"));
        assertThat(productRepository.findAll()).hasSize(1);
    }

    @Test
    void reusingAnOperationIdWithDifferentContentIsRefused() {
        String opId = UUID.randomUUID().toString();
        String uuid = UUID.randomUUID().toString();

        push(principal, pc.deviceUuid(), op(opId, "PRODUCT_CREATED",
                productPayload(uuid, "IDEM-2", "Original name")));

        SyncPayloads.PushResponse reused = push(principal, pc.deviceUuid(), op(opId, "PRODUCT_CREATED",
                productPayload(uuid, "IDEM-2", "Something else entirely")));

        assertThat(reused.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("rejected");
            assertThat(result.code()).isEqualTo("operation_mismatch");
            assertThat(result.message()).contains("different content");
        });
        assertThat(productRepository.findByUuidAndBusinessId(uuid, 1L).orElseThrow().getName())
                .isEqualTo("Original name");
        assertThat(syncOperationRepository.count()).isEqualTo(1);
    }

    @Test
    void reusingAnOperationIdForADifferentCommandTypeIsRefused() {
        String opId = UUID.randomUUID().toString();

        push(principal, pc.deviceUuid(), op(opId, "PRODUCT_CREATED",
                productPayload(UUID.randomUUID().toString(), "IDEM-3", "Cable")));

        SyncPayloads.PushResponse reused = push(principal, pc.deviceUuid(),
                op(opId, "CUSTOMER_CREATED", Map.of("uuid", UUID.randomUUID().toString(), "name", "Ana")));

        assertThat(reused.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("rejected");
            assertThat(result.code()).isEqualTo("operation_mismatch");
        });
        assertThat(customerRepository.findAll()).isEmpty();
    }

    @Test
    void reusingAnOperationIdFromAnotherDeviceIsRefused() {
        String opId = UUID.randomUUID().toString();
        SyncPayloads.Operation created = op(opId, "PRODUCT_CREATED",
                productPayload(UUID.randomUUID().toString(), "IDEM-4", "Cable"));
        push(principal, pc.deviceUuid(), created);

        // A second device of the same business takes the lease, so its batch is
        // accepted and the duplicate operation id is what stops it.
        DeviceService.Registration tablet = register("idemowner", "Tablet");
        SyncLeaseService.HandoverResult moved = leaseService.handover(principal, tablet.deviceUuid());
        DevicePrincipal tabletPrincipal = principalOf(tablet.deviceUuid(), owner);

        SyncPayloads.PushResponse reused = pushService.push(tabletPrincipal,
                new SyncPayloads.PushRequest(tablet.deviceUuid(), moved.epoch(), List.of(created)));

        assertThat(reused.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("rejected");
            assertThat(result.code()).isEqualTo("operation_mismatch");
            assertThat(result.message()).contains("different device");
        });
        assertThat(productRepository.findAll()).hasSize(1);
    }

    @Test
    void twoBusinessesCanUseTheSameOperationIdIndependently() {
        String opId = UUID.randomUUID().toString();
        String mineUuid = UUID.randomUUID().toString();
        push(principal, pc.deviceUuid(), op(opId, "PRODUCT_CREATED",
                productPayload(mineUuid, "SHARED-1", "Mine")));

        Long otherBusiness = secondBusiness("Second Shop");
        User otherOwner = as(otherBusiness, () -> account("idemowner2", Role.OWNER));
        DeviceService.Registration otherDevice = as(otherBusiness,
                () -> register("idemowner2", "Shop two PC"));
        as(otherBusiness, () -> leaseService.claim(principalOf(otherDevice.deviceUuid(), otherOwner)));
        DevicePrincipal otherPrincipal = as(otherBusiness,
                () -> principalOf(otherDevice.deviceUuid(), otherOwner));

        String theirUuid = UUID.randomUUID().toString();
        SyncPayloads.PushResponse theirs = as(otherBusiness, () -> push(otherPrincipal,
                otherDevice.deviceUuid(),
                op(opId, "PRODUCT_CREATED", productPayload(theirUuid, "SHARED-1", "Theirs"))));

        assertThat(theirs.results()).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo("applied"));

        // The same id and the same SKU now exist in two businesses, side by side.
        TenantContext.set(1L);
        assertThat(productRepository.findByUuidAndBusinessId(mineUuid, 1L)).isPresent();
        assertThat(productRepository.findByUuidAndBusinessId(theirUuid, 1L)).isEmpty();
        assertThat(productRepository.count()).isEqualTo(2);
        assertThat(syncOperationRepository.count()).isEqualTo(2);
    }

    @Test
    void twoIdenticalRequestsAtTheSameMomentApplyExactlyOnce() throws Exception {
        String opId = UUID.randomUUID().toString();
        SyncPayloads.Operation created = op(opId, "PRODUCT_CREATED",
                productPayload(UUID.randomUUID().toString(), "IDEM-RACE", "Cable"));

        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // Both threads wait on the barrier, then send the very same command.
            Callable<SyncPayloads.PushResponse> task = () -> {
                start.await(10, TimeUnit.SECONDS);
                return TenantContext.callAs(1L, () -> pushService.push(principal,
                        new SyncPayloads.PushRequest(pc.deviceUuid(), 1L, List.of(created))));
            };

            Future<SyncPayloads.PushResponse> a = pool.submit(task);
            Future<SyncPayloads.PushResponse> b = pool.submit(task);

            List<SyncPayloads.PushResponse> responses = List.of(
                    a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));

            long applied = responses.stream()
                    .flatMap(response -> response.results().stream())
                    .filter(result -> "applied".equals(result.status()))
                    .count();
            long answered = responses.stream()
                    .flatMap(response -> response.results().stream())
                    .filter(result -> "applied".equals(result.status())
                            || "duplicate".equals(result.status()))
                    .count();

            assertThat(applied).as("the operation is applied exactly once").isEqualTo(1);
            assertThat(answered).as("both callers get an answer").isEqualTo(2);
            assertThat(productRepository.findAll()).hasSize(1);
            assertThat(syncOperationRepository.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
