package org.example.backtrackweb.config;

import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.repository.UserRepository;
import org.example.backtrackweb.service.SettingsService;
import org.example.backtrackweb.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * Runs once at startup.
 *
 * If there are no accounts yet, it creates the first owner so the system can
 * be used immediately. The password comes from APP_OWNER_PASSWORD; when that
 * is not set a random one is generated and shown once in the log.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SettingsService settingsService;

    @Value("${app.owner.username:owner}")
    private String ownerUsername;

    @Value("${app.owner.password:}")
    private String ownerPassword;

    @Value("${app.owner.full-name:Business Owner}")
    private String ownerFullName;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           SettingsService settingsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.settingsService = settingsService;
    }

    /** The first business created by the baseline migration. */
    private static final Long FIRST_BUSINESS_ID = 1L;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Startup runs without a request, so the first business is bound
        // explicitly. Everything created here (settings, first owner) belongs
        // to that business, and any other business is created later through the
        // same tenant-scoped services.
        TenantContext.runAs(FIRST_BUSINESS_ID, this::bootstrap);
    }

    private void bootstrap() {
        settingsService.get();

        if (userRepository.count() > 0) {
            return;
        }

        boolean generated = ownerPassword == null || ownerPassword.isBlank();
        String password = generated ? randomPassword() : ownerPassword;

        User owner = new User();
        owner.setUsername(ownerUsername);
        owner.setFullName(ownerFullName);
        owner.setRole(Role.OWNER);
        owner.setActive(true);
        owner.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(owner);

        if (generated) {
            log.warn("""

                    ==========================================================
                     BackTrack created your first owner account
                       username: {}
                       password: {}
                     Write this password down, then change it after login.
                     Set APP_OWNER_PASSWORD next time to choose your own.
                    ==========================================================
                    """, owner.getUsername(), password);
        } else {
            log.info("BackTrack created the owner account '{}' using APP_OWNER_PASSWORD.", owner.getUsername());
        }
    }

    private String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
