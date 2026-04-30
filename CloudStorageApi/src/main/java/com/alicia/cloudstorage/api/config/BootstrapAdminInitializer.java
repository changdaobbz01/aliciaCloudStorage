package com.alicia.cloudstorage.api.config;

import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final long defaultUserQuotaBytes;
    private final String bootstrapAdminPhone;
    private final String bootstrapAdminPassword;
    private final String bootstrapAdminNickname;
    private final String bootstrapAdminAvatarUrl;

    public BootstrapAdminInitializer(
            SysUserRepository sysUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${alicia.storage.default-user-quota-bytes:536870912}") long defaultUserQuotaBytes,
            @Value("${alicia.bootstrap-admin.phone:}") String bootstrapAdminPhone,
            @Value("${alicia.bootstrap-admin.password:}") String bootstrapAdminPassword,
            @Value("${alicia.bootstrap-admin.nickname:}") String bootstrapAdminNickname,
            @Value("${alicia.bootstrap-admin.avatar-url:}") String bootstrapAdminAvatarUrl
    ) {
        this.sysUserRepository = sysUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultUserQuotaBytes = defaultUserQuotaBytes;
        this.bootstrapAdminPhone = bootstrapAdminPhone;
        this.bootstrapAdminPassword = bootstrapAdminPassword;
        this.bootstrapAdminNickname = bootstrapAdminNickname;
        this.bootstrapAdminAvatarUrl = bootstrapAdminAvatarUrl;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (sysUserRepository.count() > 0) {
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

        if (!phoneNumber.matches("^1\\d{10}$")) {
            throw new IllegalStateException("Bootstrap admin phone number must be a valid 11-digit mainland China mobile number.");
        }

        if (password.length() < 6) {
            throw new IllegalStateException("Bootstrap admin password must be at least 6 characters long.");
        }

        SysUser admin = new SysUser();
        admin.setPhoneNumber(phoneNumber);
        admin.setNickname(resolveNickname());
        admin.setAvatarUrl(normalizeOptionalValue(bootstrapAdminAvatarUrl));
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setTokenVersion(0L);
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setStorageQuotaBytes(defaultUserQuotaBytes);
        sysUserRepository.save(admin);

        log.info("Bootstrap admin account created for phone {}", phoneNumber);
    }

    private String resolveNickname() {
        String nickname = normalizeOptionalValue(bootstrapAdminNickname);
        return nickname == null ? "\u7cfb\u7edf\u7ba1\u7406\u5458" : nickname;
    }

    private String normalizeOptionalValue(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
