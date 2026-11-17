package com.octapoint.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultAdminEmail;
    private final String defaultAdminPassword;

    public DataInitializer(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.default-admin-email:admin@octapoint.io}") String defaultAdminEmail,
            @Value("${app.security.default-admin-password:admin123}") String defaultAdminPassword) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultAdminEmail = defaultAdminEmail;
        this.defaultAdminPassword = defaultAdminPassword;
    }

    @Override
    public void run(String... args) {
        String normalizedEmail = defaultAdminEmail.trim().toLowerCase();
        if (appUserRepository.findByEmail(normalizedEmail).isEmpty()) {
            AppUser admin = new AppUser();
            admin.setId("usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            admin.setName("OctaPoint System Admin");
            admin.setEmail(normalizedEmail);
            admin.setPasswordHash(passwordEncoder.encode(defaultAdminPassword));
            admin.setRole("ADMIN");
            admin.setTenantId(null);
            appUserRepository.save(admin);
        }
    }
}
