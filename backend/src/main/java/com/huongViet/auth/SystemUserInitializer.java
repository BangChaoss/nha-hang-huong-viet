package com.huongViet.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SystemUserInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(SystemUserInitializer.class);
    public static final String SYSTEM_USER_ID = "00000000-0000-0000-0000-000000000000";

    private final UserRepository userRepository;

    public SystemUserInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsById(SYSTEM_USER_ID)) {
            User systemUser = new User(
                SYSTEM_USER_ID,
                "system",
                "system@huongviet.local",
                "0000000000",
                "$2a$10$7EqJtq98hPqEX7fNZaFWoO.8/YxV9Rvh7e1t3fQ7x8.2nFpA6.t6q",
                UserRole.ADMIN
            );
            userRepository.save(systemUser);
            logger.info("Initialized default system user with ID: {}", SYSTEM_USER_ID);
        }
    }
}
