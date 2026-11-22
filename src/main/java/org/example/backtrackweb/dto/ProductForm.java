package org.example.backtrackweb.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.backtrackweb.entity.Product;

import java.math.BigDecimal;

/**
 * Create / edit a product. Stock is NOT edited here: it only changes through
 * stock adjustments and sales so the inventory history stays truthful.
 */
public class ProductForm {

    private Long id;

    @NotBlank(message = "SKU / product code is required")
    @Size(max = 40)
    @Pattern(regexp = "^[A-Za-z0-9._-]+$",
            message = "SKU may only use letters, numbers, dot, underscore or dash")
    private String sku;

    @NotBlank(message = "Product name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 500)
    private String description;

    private Long categoryId;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.00", message = "Selling price cannot be negative")
    private BigDecimal sellingPrice;

    @NotNull(message = "Cost price is required")
    @DecimalMin(value = "0.00", message = "Cost price cannot be negative")
    private BigDecimal costPrice;

    @NotNull(message = "Minimum stock level is required")
    @Min(value = 0, message = "Minimum stock level cannot be negative")
    private Integer minStockLevel = 0;

    /** Only used when creating a product: the starting stock. */
    @Min(value = 0, message = "Opening stock cannot be negative")
    private Integer openingStock = 0;

    private boolean archived = false;

    public static ProductForm fromEntity(Product product) {
        ProductForm form = new ProductForm();
        form.id = product.getId();
        form.sku = product.getSku();
        form.name = product.getName();
        form.description = product.getDescription();
        form.categoryId = product.getCategory() == null ? null : product.getCategory().getId();
        form.sellingPrice = product.getSellingPrice();
        form.costPrice = product.getCostPrice();
        form.minStockLevel = product.getMinStockLevel();
        form.openingStock = 0;
        form.archived = product.isArchived();
        return form;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public Integer getMinStockLevel() {
        return minStockLevel;
    }

    public void setMinStockLevel(Integer minStockLevel) {
        this.minStockLevel = minStockLevel;
    }

    public Integer getOpeningStock() {
        return openingStock;
    }

    public void setOpeningStock(Integer openingStock) {
        this.openingStock = openingStock;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }
}
