package org.example.backtrackweb.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.example.backtrackweb.repository.BusinessSettingsRepository;
import org.example.backtrackweb.repository.CustomerRepository;
import org.example.backtrackweb.repository.InventoryDiscrepancyRepository;
import org.example.backtrackweb.repository.NumberSequenceRepository;
import org.example.backtrackweb.repository.PaymentRepository;
import org.example.backtrackweb.repository.ProductCategoryRepository;
import org.example.backtrackweb.repository.ProductRepository;
import org.example.backtrackweb.repository.SaleRepository;
import org.example.backtrackweb.repository.StockMovementRepository;
import org.example.backtrackweb.repository.UserRepository;
import org.example.backtrackweb.repository.BusinessRepository;
import org.example.backtrackweb.sync.repository.SyncChangeLogRepository;
import org.example.backtrackweb.sync.repository.SyncDeviceRepository;
import org.example.backtrackweb.sync.repository.SyncLeaseEventRepository;
import org.example.backtrackweb.sync.repository.SyncLeaseRepository;
import org.example.backtrackweb.sync.repository.SyncNumberLeaseRepository;
import org.example.backtrackweb.sync.repository.SyncOperationRepository;
import org.example.backtrackweb.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Base class for tests.
 *
 * It starts a REAL PostgreSQL server (the same database engine the system
 * uses in production) on a random port and points Spring at it. That means
 * tests exercise the actual SQL, constraints and transactions.
 */
@SpringBootTest
public abstract class PostgresTestBase {

    private static final EmbeddedPostgres POSTGRES;

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not start the embedded PostgreSQL", ex);
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("app.owner.username", () -> "testowner");
        registry.add("app.owner.password", () -> "testowner123");
    }

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected CustomerRepository customerRepository;
    @Autowired
    protected ProductRepository productRepository;
    @Autowired
    protected ProductCategoryRepository categoryRepository;
    @Autowired
    protected SaleRepository saleRepository;
    @Autowired
    protected PaymentRepository paymentRepository;
    @Autowired
    protected StockMovementRepository stockMovementRepository;
    @Autowired
    protected NumberSequenceRepository numberSequenceRepository;
    @Autowired
    protected BusinessSettingsRepository businessSettingsRepository;
    @Autowired
    protected InventoryDiscrepancyRepository inventoryDiscrepancyRepository;
    @Autowired
    protected BusinessRepository businessRepository;
    @Autowired
    protected SyncDeviceRepository syncDeviceRepository;
    @Autowired
    protected SyncOperationRepository syncOperationRepository;
    @Autowired
    protected SyncLeaseRepository syncLeaseRepository;
    @Autowired
    protected SyncLeaseEventRepository syncLeaseEventRepository;
    @Autowired
    protected SyncNumberLeaseRepository syncNumberLeaseRepository;
    @Autowired
    protected SyncChangeLogRepository syncChangeLogRepository;

    /**
     * Clears business data between tests. The order matters because of foreign
     * keys: children before parents.
     */
    @BeforeEach
    void cleanDatabase() {
        // Cleaning up spans every business, so nothing is bound while it runs:
        // the SyncableEntity backstop only guards writes made inside a tenant,
        // and it would rightly refuse to delete another business's rows.
        TenantContext.clear();

        // Sync rows are removed before the business data they point at.
        syncOperationRepository.deleteAllInBatch();
        syncNumberLeaseRepository.deleteAllInBatch();
        syncChangeLogRepository.deleteAllInBatch();
        syncLeaseEventRepository.deleteAllInBatch();
        syncLeaseRepository.deleteAllInBatch();
        syncDeviceRepository.deleteAllInBatch();

        // deleteAllInBatch() skips the JPA cascade, so sales are removed with
        // deleteAll() to let Hibernate delete their items and payments first.
        inventoryDiscrepancyRepository.deleteAllInBatch();
        stockMovementRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        saleRepository.deleteAll();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        numberSequenceRepository.deleteAllInBatch();
        businessSettingsRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Business 1 is created by the migration and must stay.
        businessRepository.findAll().stream()
                .filter(business -> !business.getId().equals(1L))
                .forEach(businessRepository::delete);

        // Tests drive services directly (no HTTP request), so the tenant that a
        // request would normally bind is set explicitly for business 1.
        TenantContext.set(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }
}
