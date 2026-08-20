package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityUserInputNormalizerTest {

    private final IdentityUserInputNormalizer normalizer = new IdentityUserInputNormalizer();

    @Test
    void normalizeEmailTrimsAndLowercasesEmail() {
        assertThat(normalizer.normalizeEmail(" User@Example.COM "))
                .isEqualTo("user@example.com");
    }

    @Test
    void normalizeEmailRejectsMissingEmail() {
        assertThatThrownBy(() -> normalizer.normalizeEmail(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("邮箱不能为空。");
    }

    @Test
    void normalizeEmailRejectsInvalidEmail() {
        assertThatThrownBy(() -> normalizer.normalizeEmail("not-email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请输入有效邮箱地址。");
    }

    @Test
    void normalizeOptionalEmailAllowsBlankEmail() {
        assertThat(normalizer.normalizeOptionalEmail(" ")).isNull();
    }

    @Test
    void normalizePhoneNumberTrimsValidPhoneNumber() {
        assertThat(normalizer.normalizePhoneNumber(" 13800000000 "))
                .isEqualTo("13800000000");
    }

    @Test
    void normalizePhoneNumberRejectsInvalidPhoneNumber() {
        assertThatThrownBy(() -> normalizer.normalizePhoneNumber("123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请输入 11 位手机号。");
    }

    @Test
    void normalizePhoneNumberRejectsMissingPhoneNumber() {
        assertThatThrownBy(() -> normalizer.normalizePhoneNumber(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请输入 11 位手机号。");
    }

    @Test
    void normalizeOptionalPhoneNumberAllowsBlankPhoneNumber() {
        assertThat(normalizer.normalizeOptionalPhoneNumber(" ")).isNull();
    }

    @Test
    void normalizeNicknameTrimsNickname() {
        assertThat(normalizer.normalizeNickname(" Alicia "))
                .isEqualTo("Alicia");
    }

    @Test
    void normalizeNicknameRejectsBlankNickname() {
        assertThatThrownBy(() -> normalizer.normalizeNickname(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("昵称不能为空。");
    }

    @Test
    void normalizeAvatarUrlTrimsOrReturnsNull() {
        assertThat(normalizer.normalizeAvatarUrl(" cos:user-avatars/1/avatar.png "))
                .isEqualTo("cos:user-avatars/1/avatar.png");
        assertThat(normalizer.normalizeAvatarUrl(" ")).isNull();
    }

    @Test
    void normalizeRoleDefaultsToUserAndAcceptsCaseInsensitiveRole() {
        assertThat(normalizer.normalizeRole(null)).isEqualTo(IdentityUserRole.USER);
        assertThat(normalizer.normalizeRole(" admin ")).isEqualTo(IdentityUserRole.ADMIN);
    }

    @Test
    void normalizeRoleRejectsUnknownRole() {
        assertThatThrownBy(() -> normalizer.normalizeRole("OWNER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色只能是 ADMIN 或 USER。");
    }
}
