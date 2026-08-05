package dev.vedaaxis.api.identity;

import dev.vedaaxis.api.common.ApiException;
import dev.vedaaxis.api.security.JwtService;
import dev.vedaaxis.api.security.SecureTokens;
import dev.vedaaxis.api.security.SecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class IdentityService {
    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityProperties properties;

    public IdentityService(
            UserMapper userMapper,
            RefreshTokenMapper refreshTokenMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SecurityProperties properties) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public TokenPair register(String email, String password) {
        String normalized = normalizeEmail(email);
        if (userMapper.findByEmail(normalized).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "该邮箱已注册");
        }
        UUID userId = UUID.randomUUID();
        userMapper.insert(new UserRow(
                userId.toString(), normalized, passwordEncoder.encode(password), Instant.now()));
        return issueTokens(userId, "web");
    }

    @Transactional
    public TokenPair login(String email, String password) {
        UserRow user = userMapper.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> invalidCredentials());
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw invalidCredentials();
        }
        return issueTokens(UUID.fromString(user.id()), "web");
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        String tokenHash = SecureTokens.sha256(refreshToken);
        RefreshTokenRow stored = refreshTokenMapper.findByHash(tokenHash)
                .orElseThrow(this::invalidRefreshToken);
        if (stored.revokedAt() != null || !stored.expiresAt().isAfter(Instant.now())) {
            throw invalidRefreshToken();
        }
        if (refreshTokenMapper.revoke(stored.id(), Instant.now()) != 1) {
            throw invalidRefreshToken();
        }
        return issueTokens(UUID.fromString(stored.userId()), stored.audience());
    }

    @Transactional
    public TokenPair issueTokens(UUID userId, String audience) {
        Duration accessLifetime = Duration.ofMinutes(properties.accessTokenMinutes());
        Instant accessExpiresAt = Instant.now().plus(accessLifetime);
        String accessToken = jwtService.issue(userId, audience, accessLifetime);
        String refreshToken = SecureTokens.opaqueToken(48);
        Instant now = Instant.now();
        refreshTokenMapper.insert(new RefreshTokenRow(
                UUID.randomUUID().toString(),
                userId.toString(),
                SecureTokens.sha256(refreshToken),
                audience,
                now.plus(Duration.ofDays(properties.refreshTokenDays())),
                null,
                now));
        return new TokenPair(accessToken, refreshToken, "Bearer", accessExpiresAt);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "邮箱或密码错误");
    }

    private ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "刷新令牌无效或已过期");
    }
}
