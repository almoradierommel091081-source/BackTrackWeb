package org.example.backtrackweb.sync.service;

import org.example.backtrackweb.service.NumberSequenceService;
import org.example.backtrackweb.sync.entity.SyncChangeLogEntry;
import org.example.backtrackweb.sync.repository.SyncChangeLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the change feed that pull reads.
 *
 * <p>Each business has its own counter, so sequence numbers are small, ordered
 * and independent of other businesses. The entry is written in the same
 * transaction as the change itself: either both exist or neither does.
 */
@Service
public class ChangeFeedService {

    public static final String PRODUCT = "PRODUCT";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String CATEGORY = "CATEGORY";
    public static final String SETTINGS = "SETTINGS";
    public static final String SALE = "SALE";
    public static final String STOCK = "STOCK";

    public static final String UPSERT = "UPSERT";
    public static final String DELETE = "DELETE";
    public static final String CANCELLED = "CANCELLED";

    private final SyncChangeLogRepository repository;
    private final NumberSequenceService numberSequenceService;

    public ChangeFeedService(SyncChangeLogRepository repository,
                             NumberSequenceService numberSequenceService) {
        this.repository = repository;
        this.numberSequenceService = numberSequenceService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long record(Long businessId, String entityType, String entityUuid, String operation) {
        long serverSeq = numberSequenceService.nextSyncSequence(businessId);
        SyncChangeLogEntry entry = new SyncChangeLogEntry();
        entry.setBusinessId(businessId);
        entry.setEntityType(entityType);
        entry.setEntityUuid(entityUuid);
        entry.setOperation(operation);
        entry.setServerSeq(serverSeq);
        repository.save(entry);
        return serverSeq;
    }
}
