package com.alicia.cloudstorage.api.identity;

record IdentityLoginPayload(
        String token,
        IdentityUserPayload user
) {

    IdentityLoginSession toSession() {
        if (token == null || token.isBlank() || user == null) {
            throw new IllegalStateException("身份服务登录响应不完整。");
        }

        return new IdentityLoginSession(token, user.toAccount());
    }
}
