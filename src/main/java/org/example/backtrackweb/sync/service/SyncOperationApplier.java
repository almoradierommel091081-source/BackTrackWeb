package org.example.backtrackweb.sync.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.example.backtrackweb.dto.SaleForm;
import org.example.backtrackweb.dto.SaleItemForm;
import org.example.backtrackweb.entity.Customer;
import org.example.backtrackweb.entity.PaymentMethod;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.exception.NotFoundException;
import org.example.backtrackweb.repository.CustomerRepository;
import org.example.backtrackweb.repository.ProductRepository;
import org.example.backtrackweb.repository.UserRepository;
import org.example.backtrackweb.service.SaleService;
import org.example.backtrackweb.sync.SyncConflictException;
import org.example.backtrackweb.sync.dto.SyncPayloads;
import org.example.backtrackweb.sync.entity.SyncOperation;
import org.example.backtrackweb.sync.security.DevicePrincipal;
import org.example.backtrackweb.sync.repository.SyncOperationRepository;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Applies one command from a device, exactly once.
 *
 * <p>The ledger row is written in the same transaction as the change, so a
 * command can never be half-applied or applied twice: a retry finds the ledger
 * row and gets the original answer back.
 *
 * <p>Each command runs in its own transaction, so one rejected command (an
 * edit that conflicts, a product that does not exist yet) does not roll back the
 * commands that came before it.
 *
 * <p>Every command also takes the lease row lock (see
 * {@link SyncLeaseService#lockAndVerify}) as its first act inside its own
 * transaction. That is what makes the epoch check meaningful: the lock is held
 * until the command commits, so a forced handover cannot slip in between the
 * check and the commit. Commands are therefore serialised against handovers and
 * against each other, which also makes two identical concurrent pushes
 * impossible to apply twice.
 */
@Service
public class SyncOperationApplier {

    private final SyncOperationRepository operationRepository;
    private final org.example.backtrackweb.sync.repository.SyncDeviceRepository deviceRepository;
    private final NumberBlockService numberBlockService;
    private final ChangeFeedService changeFeedService;
    private final SyncMasterDataService masterDataService;
    private final SyncSerializers serializers;
    private final SyncLeaseService leaseService;
    private final OperationFingerprint fingerprint;
    private final SaleService saleService;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public SyncOperationApplier(SyncOperationRepository operationRepository,
                                org.example.backtrackweb.sync.repository.SyncDeviceRepository deviceRepository,
                                NumberBlockService numberBlockService,
                                ChangeFeedService changeFeedService,
                                SyncMasterDataService masterDataService,
                                SyncSerializers serializers,
                                SyncLeaseService leaseService,
                                OperationFingerprint fingerprint,
                                SaleService saleService,
                                ProductRepository productRepository,
                                CustomerRepository customerRepository,
                                UserRepository userRepository,
                                ObjectMapper objectMapper) {
        this.operationRepository = operationRepository;
        this.deviceRepository = deviceRepository;
        this.numberBlockService = numberBlockService;
        this.changeFeedService = changeFeedService;
        this.masterDataService = masterDataService;
        this.serializers = serializers;
        this.leaseService = leaseService;
        this.fingerprint = fingerprint;
        this.saleService = saleService;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Applies one command inside its own transaction.
     *
     * @param leaseEpoch the epoch the client believes it holds; verified on the
     *                   locked lease row before anything is written
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncPayloads.OperationResult apply(DevicePrincipal principal, SyncPayloads.Operation operation,
                                              long leaseEpoch) {
        String opId = operation.opId();
        Long businessId = principal.businessId();
        String content = fingerprint.of(businessId, principal.deviceId(), operation.type(),
                operation.payload());

        // A retry of something already applied is answered from the ledger. This
        // is deliberately done before the fence check: a device that has since
        // lost the lease must still be able to collect the answer to a command
        // that did commit, without being able to write anything new.
        SyncOperation existing = operationRepository.findByOpIdAndBusinessId(opId, businessId).orElse(null);
        if (existing != null) {
            return replay(existing, principal, content);
        }

        // Take the lease row lock first: it is held until this transaction
        // commits, so a forced handover either happens entirely before this
        // command (and the check below fails) or entirely after it.
        leaseService.lockAndVerify(businessId, principal.deviceId(), leaseEpoch);

        // Under the lock, look once more. A concurrent identical push may have
        // committed while this one waited for the lock.
        existing = operationRepository.findByOpIdAndBusinessId(opId, businessId).orElse(null);
        if (existing != null) {
            return replay(existing, principal, content);
        }

        Applied applied = switch (operation.type() == null ? "" : operation.type().toUpperCase(Locale.ROOT)) {
            case "PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_UPSERT" -> applyProduct(operation, principal);
            case "CUSTOMER_CREATED", "CUSTOMER_UPDATED", "CUSTOMER_UPSERT" -> applyCustomer(operation);
            case "CATEGORY_CREATED", "CATEGORY_UPDATED", "CATEGORY_UPSERT" -> applyCategory(operation);
            case "SETTINGS_UPDATED" -> applySettings(operation);
            case "STOCK_DELTA", "STOCK_ADJUSTED" -> applyStock(operation, principal);
            case "SALE_CREATED" -> applySale(operation, principal);
            default -> throw new BusinessException("Unknown operation type: " + operation.type());
        };

        long serverSeq = changeFeedService.record(businessId, applied.entityType(),
                applied.entityUuid(), applied.operation());

        SyncOperation ledger = new SyncOperation();
        ledger.setOpId(opId);
        ledger.setBusinessId(businessId);
        ledger.setDeviceId(principal.deviceId());
        ledger.setOpType(operation.type());
        ledger.setAggregateUuid(applied.entityUuid());
        ledger.setServerSeq(serverSeq);
        ledger.setResultJson(applied.data().toString());
        ledger.setPayloadHash(content);
        // Flushed here so a duplicate key is raised inside this method, with the
        // rest of the command, instead of surfacing at commit.
        operationRepository.saveAndFlush(ledger);

        return SyncPayloads.OperationResult.applied(opId, applied.message(), applied.data());
    }

    /**
     * Reads the ledger for a command that may have just been applied by a
     * concurrent, identical push. Used after a constraint violation to answer
     * the loser with the winner's result instead of a bare rejection.
     *
     * @return the duplicate result, or {@code null} when nothing was applied
     */
    @Transactional(readOnly = true)
    public SyncPayloads.OperationResult replayIfApplied(DevicePrincipal principal,
                                                        SyncPayloads.Operation operation) {
        Long businessId = principal.businessId();
        SyncOperation existing = operationRepository
                .findByOpIdAndBusinessId(operation.opId(), businessId)
                .orElse(null);
        if (existing == null) {
            return null;
        }
        String content = fingerprint.of(businessId, principal.deviceId(), operation.type(),
                operation.payload());
        return replay(existing, principal, content);
    }

    /**
     * Answers a repeated operation id.
     *
     * <p>An exact retry - same business, same device, same command type, same
     * payload - gets the original result back. A reused id carrying anything
     * else is refused rather than answered, because returning the old result
     * would silently discard the new command and applying it would break the
     * exactly-once promise.
     */
    private SyncPayloads.OperationResult replay(SyncOperation existing, DevicePrincipal principal,
                                                String content) {
        if (!Objects.equals(existing.getDeviceId(), principal.deviceId())) {
            throw new SyncConflictException("operation_mismatch",
                    "Operation " + existing.getOpId() + " was already applied by a different device "
                            + "of this business. Every command needs its own opId.");
        }
        if (!content.equals(existing.getPayloadHash())) {
            throw new SyncConflictException("operation_mismatch",
                    "Operation " + existing.getOpId() + " was already applied with different content "
                            + "(" + existing.getOpType() + "). Every command needs its own opId.");
        }
        return SyncPayloads.OperationResult.duplicate(existing.getOpId(),
                readTree(existing.getResultJson()));
    }

    private record Applied(String entityType, String entityUuid, String operation, String message,
                           JsonNode data) {
    }

    private Applied applyProduct(SyncPayloads.Operation operation, DevicePrincipal principal) {
        SyncPayloads.Product payload = payload(operation, SyncPayloads.Product.class);
        User actor = actor(principal);
        Product product = masterDataService.upsertProduct(payload, actor);
        return new Applied(ChangeFeedService.PRODUCT, product.getUuid(), ChangeFeedService.UPSERT,
                "Product saved (version " + product.getVersion() + ").", serializers.product(product));
    }

    private Applied applyCustomer(SyncPayloads.Operation operation) {
        SyncPayloads.Customer payload = payload(operation, SyncPayloads.Customer.class);
        Customer customer = masterDataService.upsertCustomer(payload);
        return new Applied(ChangeFeedService.CUSTOMER, customer.getUuid(), ChangeFeedService.UPSERT,
                "Customer saved (version " + customer.getVersion() + ").", serializers.customer(customer));
    }

    private Applied applyCategory(SyncPayloads.Operation operation) {
        SyncPayloads.Category payload = payload(operation, SyncPayloads.Category.class);
        var category = masterDataService.upsertCategory(payload);
        return new Applied(ChangeFeedService.CATEGORY, category.getUuid(), ChangeFeedService.UPSERT,
                "Category saved (version " + category.getVersion() + ").", serializers.category(category));
    }

    private Applied applySettings(SyncPayloads.Operation operation) {
        SyncPayloads.Settings payload = payload(operation, SyncPayloads.Settings.class);
        var settings = masterDataService.updateSettings(payload);
        return new Applied(ChangeFeedService.SETTINGS, settings.getUuid(), ChangeFeedService.UPSERT,
                "Settings saved.", serializers.settings(settings));
    }

    private Applied applyStock(SyncPayloads.Operation operation, DevicePrincipal principal) {
        SyncPayloads.StockDelta payload = payload(operation, SyncPayloads.StockDelta.class);
        Product product = masterDataService.applyStockDelta(payload, actor(principal));
        return new Applied(ChangeFeedService.STOCK, product.getUuid(), ChangeFeedService.UPSERT,
                "Stock updated to " + product.getStockQuantity() + ".", serializers.product(product));
    }

    private Applied applySale(SyncPayloads.Operation operation, DevicePrincipal principal) {
        Long businessId = principal.businessId();
        SyncPayloads.Sale payload = payload(operation, SyncPayloads.Sale.class);
        if (payload.saleNumber() == null || payload.saleNumber().isBlank()) {
            throw new BusinessException("An offline sale must carry the receipt number it was given.");
        }
        if (payload.saleUuid() == null || payload.saleUuid().isBlank()) {
            throw new BusinessException("An offline sale must carry its uuid.");
        }
        if (payload.items() == null || payload.items().isEmpty()) {
            throw new BusinessException("An offline sale must contain at least one line.");
        }

        // The receipt number must come from a block the server issued here.
        org.example.backtrackweb.sync.entity.SyncDevice device = deviceRepository
                .findById(principal.deviceId())
                .orElseThrow(() -> new BusinessException("This device is no longer registered."));
        numberBlockService.assertIssuedTo(businessId, device, payload.saleNumber().trim());

        SaleForm form = new SaleForm();
        form.setPaymentMethod(paymentMethod(payload.paymentMethod()));
        form.setDiscountAmount(payload.discountAmount());
        form.setAmountPaid(payload.amountPaid());
        form.setNotes(payload.notes());

        if (payload.customerUuid() != null && !payload.customerUuid().isBlank()) {
            Customer customer = customerRepository.findByUuidAndBusinessId(payload.customerUuid(), businessId)
                    .orElseThrow(() -> new BusinessException(
                            "That customer does not exist on the server yet."));
            form.setCustomerId(customer.getId());
        }

        List<SaleItemForm> lines = new ArrayList<>();
        for (SyncPayloads.SaleLine line : payload.items()) {
            Product product = productRepository.findByUuidAndBusinessId(line.productUuid(), businessId)
                    .orElseThrow(() -> new BusinessException(
                            "Product " + line.productUuid() + " does not exist on the server yet."));
            SaleItemForm item = new SaleItemForm();
            item.setProductId(product.getId());
            item.setQuantity(line.quantity() == null ? 1 : line.quantity());
            lines.add(item);
        }
        form.setItems(lines);

        User cashier = userRepository.findById(principal.userId())
                .orElseThrow(() -> new BusinessException("The account that registered this device is gone."));
        Sale sale = saleService.createSaleFromSync(form, cashier, payload.saleNumber().trim(),
                payload.saleUuid().trim());

        String message = sale.isStockConflict()
                ? "Sale accepted; stock went negative and is flagged for review."
                : "Sale accepted.";
        return new Applied(ChangeFeedService.SALE, sale.getUuid(), ChangeFeedService.UPSERT,
                message, serializers.sale(sale));
    }

    private PaymentMethod paymentMethod(String value) {
        if (value == null || value.isBlank()) {
            return PaymentMethod.CASH;
        }
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Unknown payment method: " + value);
        }
    }

    /** The account that registered the device, recorded as the actor of the change. */
    private User actor(DevicePrincipal principal) {
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new BusinessException("The account that registered this device is gone."));
    }

    private <T> T payload(SyncPayloads.Operation operation, Class<T> type) {
        if (operation.payload() == null || operation.payload().isNull()) {
            throw new BusinessException("The operation has no payload.");
        }
        try {
            return objectMapper.treeToValue(operation.payload(), type);
        } catch (Exception ex) {
            throw new BusinessException("The operation payload is not valid: " + ex.getMessage());
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }
}
