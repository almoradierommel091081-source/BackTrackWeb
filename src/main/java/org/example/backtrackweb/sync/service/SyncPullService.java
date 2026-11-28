package org.example.backtrackweb.sync.service;

import tools.jackson.databind.JsonNode;
import org.example.backtrackweb.repository.BusinessSettingsRepository;
import org.example.backtrackweb.repository.CustomerRepository;
import org.example.backtrackweb.repository.ProductCategoryRepository;
import org.example.backtrackweb.repository.ProductRepository;
import org.example.backtrackweb.repository.SaleRepository;
import org.example.backtrackweb.sync.dto.SyncPayloads;
import org.example.backtrackweb.sync.entity.SyncChangeLogEntry;
import org.example.backtrackweb.sync.repository.SyncChangeLogRepository;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends a device everything that changed after the sequence number it last saw.
 *
 * <p>The feed is per business and strictly ordered, so a device that stores the
 * last sequence number it applied can always catch up, and a device that missed
 * something only has to ask again from its stored position.
 */
@Service
public class SyncPullService {

    private static final int MAX_LIMIT = 500;

    private final SyncChangeLogRepository changeLogRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final ProductCategoryRepository categoryRepository;
    private final BusinessSettingsRepository settingsRepository;
    private final SaleRepository saleRepository;
    private final SyncSerializers serializers;

    public SyncPullService(SyncChangeLogRepository changeLogRepository,
                           ProductRepository productRepository,
                           CustomerRepository customerRepository,
                           ProductCategoryRepository categoryRepository,
                           BusinessSettingsRepository settingsRepository,
                           SaleRepository saleRepository,
                           SyncSerializers serializers) {
        this.changeLogRepository = changeLogRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.categoryRepository = categoryRepository;
        this.settingsRepository = settingsRepository;
        this.saleRepository = saleRepository;
        this.serializers = serializers;
    }

    @Transactional(readOnly = true)
    public SyncPayloads.PullResponse pull(long since, int limit) {
        Long businessId = TenantContext.require();
        long from = Math.max(0, since);
        int size = limit <= 0 ? 200 : Math.min(limit, MAX_LIMIT);

        List<SyncChangeLogEntry> entries = changeLogRepository
                .findByBusinessIdAndServerSeqGreaterThanOrderByServerSeqAsc(businessId, from,
                        PageRequest.of(0, size + 1));

        boolean hasMore = entries.size() > size;
        if (hasMore) {
            entries = entries.subList(0, size);
        }

        List<SyncPayloads.Change> changes = new ArrayList<>(entries.size());
        for (SyncChangeLogEntry entry : entries) {
            changes.add(new SyncPayloads.Change(entry.getServerSeq(), entry.getEntityType(),
                    entry.getEntityUuid(), entry.getOperation(),
                    entry.getChangedAt() == null ? null : entry.getChangedAt().toString(),
                    dataFor(businessId, entry)));
        }

        long toSeq = entries.isEmpty() ? from : entries.get(entries.size() - 1).getServerSeq();
        long latest = changeLogRepository.maxServerSeq(businessId);
        return new SyncPayloads.PullResponse(from, toSeq, latest, hasMore, changes);
    }

    private JsonNode dataFor(Long businessId, SyncChangeLogEntry entry) {
        return switch (entry.getEntityType()) {
            case ChangeFeedService.PRODUCT -> productRepository
                    .findByUuidAndBusinessId(entry.getEntityUuid(), businessId)
                    .map(serializers::product)
                    .orElse(null);
            case ChangeFeedService.CUSTOMER -> customerRepository
                    .findByUuidAndBusinessId(entry.getEntityUuid(), businessId)
                    .map(serializers::customer)
                    .orElse(null);
            case ChangeFeedService.CATEGORY -> categoryRepository
                    .findByUuidAndBusinessId(entry.getEntityUuid(), businessId)
                    .map(serializers::category)
                    .orElse(null);
            case ChangeFeedService.SETTINGS -> settingsRepository
                    .findByBusinessId(businessId)
                    .map(serializers::settings)
                    .orElse(null);
            case ChangeFeedService.SALE -> saleRepository
                    .findDetailedByUuidAndBusinessId(entry.getEntityUuid(), businessId)
                    .map(serializers::sale)
                    .orElse(null);
            default -> serializers.reference(entry.getEntityUuid());
        };
    }
}
