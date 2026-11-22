package org.example.backtrackweb.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.example.backtrackweb.repository.BusinessSettingsRepository;
import org.example.backtrackweb.repository.CustomerRepository;
import org.example.backtrackweb.repository.NumberSequenceRepository;
import org.example.backtrackweb.repository.PaymentRepository;
import org.example.backtrackweb.repository.ProductCategoryRepository;
import org.example.backtrackweb.repository.ProductRepository;
import org.example.backtrackweb.repository.SaleRepository;
import org.example.backtrackweb.repository.StockMovementRepository;
import org.example.backtrackweb.repository.UserRepository;
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

    /**
     * Clears business data between tests. The order matters because of foreign
     * keys: children before parents.
     */
    @BeforeEach
    void cleanDatabase() {
        // deleteAllInBatch() skips the JPA cascade, so sales are removed with
        // deleteAll() to let Hibernate delete their items and payments first.
        stockMovementRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        saleRepository.deleteAll();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        numberSequenceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}
