package org.example.backtrackweb.sync.dto;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

/**
 * The JSON shapes the clients send and receive.
 *
 * <p>Everything is addressed by {@code uuid}, never by database id, because a
 * device creates records offline and has no ids to use. Master data carries the
 * {@code baseVersion} the device edited, which is how a conflicting edit is
 * detected instead of silently overwriting newer data.
 */
public final class SyncPayloads {

    private SyncPayloads() {
    }

    /** Body of POST /api/v1/sync/push. */
    public record PushRequest(String deviceUuid, Long leaseEpoch, List<Operation> operations) {
    }

    /** One command from a device; {@code opId} is unique per command. */
    public record Operation(String opId, String type, JsonNode payload) {
    }

    /** Answer for one command. */
    public record OperationResult(String opId, String status, String code, String message,
                                 JsonNode data) {

        public static OperationResult applied(String opId, String message, JsonNode data) {
            return new OperationResult(opId, "applied", null, message, data);
        }

        public static OperationResult duplicate(String opId, JsonNode data) {
            return new OperationResult(opId, "duplicate", null,
                    "Already applied earlier; the original result is returned.", data);
        }

        public static OperationResult rejected(String opId, String code, String message, JsonNode data) {
            return new OperationResult(opId, "rejected", code, message, data);
        }
    }

    /** Body of the push answer. */
    public record PushResponse(long serverSeq, long leaseEpoch, List<OperationResult> results) {
    }

    /** One entry of the change feed. */
    public record Change(long serverSeq, String entityType, String entityUuid, String operation,
                         String changedAt, JsonNode data) {
    }

    /** Body of GET /api/v1/sync/pull. */
    public record PullResponse(long fromSeq, long toSeq, long latestSeq, boolean hasMore,
                               List<Change> changes) {
    }

    /** Product as created or edited on a device. */
    public record Product(String uuid, Long baseVersion, String sku, String name, String description,
                          String categoryUuid, BigDecimal sellingPrice, BigDecimal costPrice,
                          Integer minStockLevel, Integer stockDelta, Boolean archived) {
    }

    /** Customer as created or edited on a device. */
    public record Customer(String uuid, Long baseVersion, String name, String phone, String email,
                           String address, String notes, Boolean archived) {
    }

    /** Category as created or edited on a device. */
    public record Category(String uuid, Long baseVersion, String name) {
    }

    /** Business settings as edited on a device. */
    public record Settings(Long baseVersion, String businessName, String address, String contactNumber,
                           String email, String currency, String logoUrl, String receiptFooter,
                           Integer defaultMinStockLevel) {
    }

    /** One line of an offline sale. */
    public record SaleLine(String productUuid, Integer quantity) {
    }

    /**
     * A sale completed while offline. The receipt number was taken from a block
     * issued by the server, so it is final and is never renumbered.
     */
    public record Sale(String saleUuid, String saleNumber, String customerUuid, String paymentMethod,
                       BigDecimal discountAmount, BigDecimal amountPaid, BigDecimal changeAmount,
                       String notes, String soldAt, List<SaleLine> items) {
    }

    /** A stock change, always sent as a delta. */
    public record StockDelta(String productUuid, Integer delta, String reason) {
    }

    /** Body of POST /api/v1/sync/number-block. */
    public record NumberBlockRequest(Integer count) {
    }
}
