package com.huongViet.config;

import com.huongViet.auth.User;
import com.huongViet.auth.UserRepository;
import com.huongViet.auth.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeder component to initialize the default administrator account
 * on first startup if no admin account exists yet.
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.email:admin@huongviet.com}")
    private String adminEmail;

    @Value("${app.admin.phone:0999999999}")
    private String adminPhone;

    @Value("${app.admin.password:Admin@123456}")
    private String adminPassword;

    public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        boolean usernameExists = userRepository.existsByUsernameAndDeletedAtIsNull(adminUsername);
        boolean emailExists = userRepository.existsByEmailAndDeletedAtIsNull(adminEmail);

        if (!usernameExists && !emailExists) {
            User admin = new User(
                    UUID.randomUUID().toString(),
                    adminUsername,
                    adminEmail,
                    adminPhone,
                    passwordEncoder.encode(adminPassword),
                    UserRole.ADMIN
            );

            userRepository.save(admin);
            logger.info("=========================================================");
            logger.info(" Default ADMIN account initialized successfully:");
            logger.info(" - Username: {}", adminUsername);
            logger.info(" - Email:    {}", adminEmail);
            logger.info(" - Password: {}", adminPassword);
            logger.info(" - Role:     {}", UserRole.ADMIN);
            logger.info("=========================================================");
        } else {
            logger.info("Admin account already exists ({}/{}). Skipping admin seed.", adminUsername, adminEmail);
        }
    }
}
