package dev.vedaaxis.api.security;

import dev.vedaaxis.api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtService.AuthenticatedUser user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请先登录");
        }
        return user.userId();
    }
}
