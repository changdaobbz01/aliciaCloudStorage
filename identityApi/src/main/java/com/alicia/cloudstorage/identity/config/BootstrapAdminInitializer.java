package com.alicia.cloudstorage.identity.config;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import com.alicia.cloudstorage.identity.service.IdentityUserCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final IdentityUserRepository identityUserRepository;
    private final IdentityUserCreationService identityUserCreationService;
    private final String bootstrapAdminPhone;
    private final String bootstrapAdminPassword;
    private final String bootstrapAdminNickname;
    private final String bootstrapAdminAvatarUrl;

    public BootstrapAdminInitializer(
            IdentityUserRepository identityUserRepository,
            IdentityUserCreationService identityUserCreationService,
            @Value("${alicia.bootstrap-admin.phone:}") String bootstrapAdminPhone,
            @Value("${alicia.bootstrap-admin.password:}") String bootstrapAdminPassword,
            @Value("${alicia.bootstrap-admin.nickname:}") String bootstrapAdminNickname,
            @Value("${alicia.bootstrap-admin.avatar-url:}") String bootstrapAdminAvatarUrl
    ) {
        this.identityUserRepository = identityUserRepository;
        this.identityUserCreationService = identityUserCreationService;
        this.bootstrapAdminPhone = bootstrapAdminPhone;
        this.bootstrapAdminPassword = bootstrapAdminPassword;
        this.bootstrapAdminNickname = bootstrapAdminNickname;
        this.bootstrapAdminAvatarUrl = bootstrapAdminAvatarUrl;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (identityUserRepository.count() > 0) {
            return;
        }

        String phoneNumber = normalizeOptionalValue(bootstrapAdminPhone);
        String password = normalizeOptionalValue(bootstrapAdminPassword);

        if (phoneNumber == null && password == null) {
            log.info("Bootstrap admin creation skipped because no bootstrap credentials were provided.");
            return;
        }

        if (phoneNumber == null || password == null) {
            throw new IllegalStateException("Bootstrap admin requires both phone and password when one of them is configured.");
        }

        IdentityUser admin = createBootstrapAdmin(phoneNumber, password);

        log.info("Bootstrap admin account created for phone {}", admin.getPhoneNumber());
    }

    private IdentityUser createBootstrapAdmin(String phoneNumber, String password) {
        try {
            return identityUserCreationService.createBootstrapAdmin(
                    phoneNumber,
                    password,
                    bootstrapAdminNickname,
                    bootstrapAdminAvatarUrl
            );
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid bootstrap admin configuration: " + ex.getMessage(), ex);
        }
    }

    private String normalizeOptionalValue(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
