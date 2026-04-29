package com.alicia.cloudstorage.api.config;

import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final SysUserRepository sysUserRepository;
    private final StorageNodeRepository storageNodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final long defaultUserQuotaBytes;

    public DemoDataInitializer(
            SysUserRepository sysUserRepository,
            StorageNodeRepository storageNodeRepository,
            PasswordEncoder passwordEncoder,
            @Value("${alicia.storage.default-user-quota-bytes:536870912}") long defaultUserQuotaBytes
    ) {
        this.sysUserRepository = sysUserRepository;
        this.storageNodeRepository = storageNodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultUserQuotaBytes = defaultUserQuotaBytes;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (sysUserRepository.count() > 0) {
            return;
        }

        SysUser admin = createUser("13800000000", "系统管理员", "https://api.dicebear.com/9.x/initials/svg?seed=Admin", "Admin@123", UserRole.ADMIN);
        SysUser demoUser = createUser("13900000000", "Alicia", "https://api.dicebear.com/9.x/initials/svg?seed=Alicia", "User@123", UserRole.USER);

        seedAdminDrive(admin.getId());
        seedUserDrive(demoUser.getId());
    }

    /**
     * 创建系统初始化账号，供首次启动时直接登录体验。
     */
    private SysUser createUser(String phoneNumber, String nickname, String avatarUrl, String password, UserRole role) {
        SysUser user = new SysUser();
        user.setPhoneNumber(phoneNumber);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(defaultUserQuotaBytes);
        return sysUserRepository.save(user);
    }

    /**
     * 初始化管理员账号的基础目录结构。
     */
    private void seedAdminDrive(Long ownerId) {
        StorageNode documents = storageNodeRepository.save(folder(ownerId, null, "文档"));
        storageNodeRepository.save(folder(ownerId, null, "图片"));
        storageNodeRepository.save(folder(ownerId, null, "视频"));
        storageNodeRepository.save(folder(ownerId, documents.getId(), "合同"));
    }

    /**
     * 初始化普通用户账号的基础目录结构。
     */
    private void seedUserDrive(Long ownerId) {
        storageNodeRepository.save(folder(ownerId, null, "共享资料"));
        storageNodeRepository.save(folder(ownerId, null, "工作笔记"));
    }

    /**
     * 构建一个文件夹类型的存储节点。
     */
    private StorageNode folder(Long ownerId, Long parentId, String name) {
        StorageNode node = new StorageNode();
        node.setOwnerId(ownerId);
        node.setParentId(parentId);
        node.setNodeName(name);
        node.setNodeType(NodeType.FOLDER);
        node.setFileSize(0L);
        return node;
    }
}
