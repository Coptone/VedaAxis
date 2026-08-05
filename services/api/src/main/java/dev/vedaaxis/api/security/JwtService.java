package dev.vedaaxis.api.security;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import dev.vedaaxis.api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public JwtService(ObjectMapper objectMapper, SecurityProperties properties) {
        this.objectMapper = objectMapper;
        this.secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("VEDAAXIS_JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
    }

    public String issue(UUID userId, String audience, Duration lifetime) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userId.toString());
        claims.put("aud", audience);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(lifetime).getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());

        try {
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = encode(objectMapper.writeValueAsBytes(claims));
            String unsigned = header + "." + payload;
            return unsigned + "." + encode(sign(unsigned.getBytes(StandardCharsets.US_ASCII)));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to encode access token", exception);
        }
    }

    public AuthenticatedUser verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw invalidToken();
            }
            byte[] expected = sign((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw invalidToken();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            long expiresAt = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= expiresAt) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "访问令牌已过期");
            }
            return new AuthenticatedUser(
                    UUID.fromString(String.valueOf(claims.get("sub"))),
                    String.valueOf(claims.get("aud")));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidToken();
        }
    }

    private byte[] sign(byte[] content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(content);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign access token", exception);
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "访问令牌无效");
    }

    public record AuthenticatedUser(UUID userId, String audience) {
    }
}
