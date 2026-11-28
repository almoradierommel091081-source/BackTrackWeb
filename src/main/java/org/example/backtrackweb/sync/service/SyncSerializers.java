package org.example.backtrackweb.sync.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.example.backtrackweb.entity.BusinessSettings;
import org.example.backtrackweb.entity.Customer;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.ProductCategory;
import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.entity.SaleItem;
import org.springframework.stereotype.Component;

/**
 * Turns entities into the JSON a device receives.
 *
 * <p>Ids are never included: a device only ever needs a uuid, and leaving the
 * database ids out means a client cannot ask for a record by an id it saw in
 * another business.
 */
@Component
public class SyncSerializers {

    private final ObjectMapper objectMapper;

    public SyncSerializers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode product(Product product) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("uuid", product.getUuid());
        node.put("version", product.getVersion());
        node.put("sku", product.getSku());
        node.put("name", product.getName());
        node.put("description", product.getDescription());
        node.put("categoryUuid", product.getCategory() == null ? null : product.getCategory().getUuid());
        node.put("sellingPrice", product.getSellingPrice());
        node.put("costPrice", product.getCostPrice());
        node.put("stockQuantity", product.getStockQuantity());
        node.put("minStockLevel", product.getMinStockLevel());
        node.put("archived", product.isArchived());
        node.put("updatedAt", product.getUpdatedAt() == null ? null : product.getUpdatedAt().toString());
        return node;
    }

    public ObjectNode customer(Customer customer) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("uuid", customer.getUuid());
        node.put("version", customer.getVersion());
        node.put("name", customer.getName());
        node.put("phone", customer.getPhone());
        node.put("email", customer.getEmail());
        node.put("address", customer.getAddress());
        node.put("notes", customer.getNotes());
        node.put("archived", customer.isArchived());
        node.put("updatedAt", customer.getUpdatedAt() == null ? null : customer.getUpdatedAt().toString());
        return node;
    }

    public ObjectNode category(ProductCategory category) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("uuid", category.getUuid());
        node.put("version", category.getVersion());
        node.put("name", category.getName());
        return node;
    }

    public ObjectNode settings(BusinessSettings settings) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("uuid", settings.getUuid());
        node.put("businessName", settings.getBusinessName());
        node.put("address", settings.getAddress());
        node.put("contactNumber", settings.getContactNumber());
        node.put("email", settings.getEmail());
        node.put("currency", settings.getCurrency());
        node.put("logoUrl", settings.getLogoUrl());
        node.put("receiptFooter", settings.getReceiptFooter());
        node.put("defaultMinStockLevel", settings.getDefaultMinStockLevel());
        node.put("updatedAt", settings.getUpdatedAt() == null ? null : settings.getUpdatedAt().toString());
        return node;
    }

    public ObjectNode sale(Sale sale) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("uuid", sale.getUuid());
        node.put("saleNumber", sale.getSaleNumber());
        node.put("status", sale.getStatus().name());
        node.put("paymentStatus", sale.getPaymentStatus().name());
        node.put("paymentMethod", sale.getPaymentMethod().name());
        node.put("subtotal", sale.getSubtotal());
        node.put("discountAmount", sale.getDiscountAmount());
        node.put("totalAmount", sale.getTotalAmount());
        node.put("amountPaid", sale.getAmountPaid());
        node.put("changeAmount", sale.getChangeAmount());
        node.put("stockConflict", sale.isStockConflict());
        node.put("customerUuid", sale.getCustomer() == null ? null : sale.getCustomer().getUuid());
        node.put("cashier", sale.getCashier().getUsername());
        node.put("createdAt", sale.getCreatedAt() == null ? null : sale.getCreatedAt().toString());
        var items = node.putArray("items");
        for (SaleItem item : sale.getItems()) {
            ObjectNode line = items.addObject();
            line.put("productUuid", item.getProduct().getUuid());
            line.put("productName", item.getProductName());
            line.put("quantity", item.getQuantity());
            line.put("unitPrice", item.getUnitPrice());
            line.put("lineTotal", item.getLineTotal());
        }
        return node;
    }

    public JsonNode reference(String entityUuid) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("uuid", entityUuid);
        return node;
    }
}
