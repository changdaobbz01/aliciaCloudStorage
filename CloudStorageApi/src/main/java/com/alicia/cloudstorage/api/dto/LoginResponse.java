package com.alicia.cloudstorage.api.dto;

public record LoginResponse(
        String token,
        UserProfileResponse user
) {
}
