package com.supplychainsentinel.config;

import com.supplychainsentinel.entity.Role;
import com.supplychainsentinel.entity.User;
import com.supplychainsentinel.repository.RoleRepository;
import com.supplychainsentinel.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates an initial admin user from environment variables if no admin exists.
 *
 * <p>Requires the following env vars (all optional — if absent, a warning is logged):
 * <ul>
 *   <li>{@code INITIAL_ADMIN_USERNAME}</li>
 *   <li>{@code INITIAL_ADMIN_PASSWORD}</li>
 *   <li>{@code INITIAL_ADMIN_EMAIL}</li>
 * </ul>
 *
 * <p>Never hardcodes credentials. Skipped entirely in the {@code test} profile so
 * tests can provision their own users.</p>
 */
@Component
@Profile("!test")
public class AdminProvisioningRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminProvisioningRunner.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.email:}")
    private String adminEmail;

    public AdminProvisioningRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Skip if admin credentials not provided
        if (adminUsername.isBlank() || adminPassword.isBlank() || adminEmail.isBlank()) {
            log.warn("No initial admin credentials provided. " +
                     "Set INITIAL_ADMIN_USERNAME, INITIAL_ADMIN_PASSWORD and INITIAL_ADMIN_EMAIL " +
                     "to provision an admin user.");
            return;
        }

        // Skip if admin user already exists
        if (userRepository.existsByUsername(adminUsername)) {
            log.debug("Admin user '{}' already exists — skipping provisioning", adminUsername);
            return;
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found in database"));

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setDisplayName("System Administrator");
        admin.setEnabled(true);
        admin.setAccountLocked(false);
        admin.setFailedLoginAttempts(0);
        admin.setRoles(Set.of(adminRole));

        userRepository.save(admin);
        log.info("Initial admin user '{}' provisioned successfully", adminUsername);
    }
}
