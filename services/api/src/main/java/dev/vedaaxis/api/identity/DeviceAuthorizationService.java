package dev.vedaaxis.api.identity;

import dev.vedaaxis.api.common.ApiException;
import dev.vedaaxis.api.security.SecureTokens;
import dev.vedaaxis.api.security.SecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DeviceAuthorizationService {
    private final DeviceAuthorizationMapper mapper;
    private final IdentityService identityService;
    private final RefreshTokenMapper refreshTokenMapper;
    private final SecurityProperties properties;

    public DeviceAuthorizationService(
            DeviceAuthorizationMapper mapper,
            IdentityService identityService,
            RefreshTokenMapper refreshTokenMapper,
            SecurityProperties properties) {
        this.mapper = mapper;
        this.identityService = identityService;
        this.refreshTokenMapper = refreshTokenMapper;
        this.properties = properties;
    }

    @Transactional
    public DeviceCodeResponse create(String deviceName) {
        String rawDeviceCode = SecureTokens.opaqueToken(40);
        String userCode = uniqueUserCode();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.deviceCodeMinutes()));
        mapper.insertAuthorization(new DeviceAuthorizationRow(
                UUID.randomUUID().toString(), SecureTokens.sha256(rawDeviceCode), userCode,
                deviceName.trim(), "PENDING", null, expiresAt, null, now));
        return new DeviceCodeResponse(rawDeviceCode, userCode, expiresAt, 3);
    }

    @Transactional
    public void approve(String userCode, UUID userId) {
        DeviceAuthorizationRow authorization = mapper.findByUserCode(normalizeUserCode(userCode))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEVICE_CODE_NOT_FOUND", "绑定码不存在"));
        if (!authorization.expiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "DEVICE_CODE_EXPIRED", "绑定码已过期");
        }
        if (mapper.approve(authorization.id(), userId.toString(), Instant.now()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_CODE_NOT_PENDING", "绑定码已被处理");
        }
    }

    @Transactional
    public DeviceTokenResponse poll(String deviceCode) {
        DeviceAuthorizationRow authorization = mapper.findByDeviceCodeHash(SecureTokens.sha256(deviceCode))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEVICE_CODE_NOT_FOUND", "设备授权不存在"));
        if (!authorization.expiresAt().isAfter(Instant.now())) {
            return new DeviceTokenResponse("EXPIRED", null, null);
        }
        if ("PENDING".equals(authorization.status())) {
            return new DeviceTokenResponse("PENDING", null, null);
        }
        if (!"APPROVED".equals(authorization.status()) || authorization.userId() == null) {
            return new DeviceTokenResponse("CONSUMED", null, null);
        }
        Instant now = Instant.now();
        if (mapper.consume(authorization.id(), now) != 1) {
            return new DeviceTokenResponse("CONSUMED", null, null);
        }
        UUID deviceId = UUID.randomUUID();
        mapper.insertDevice(new AuthorizedDeviceRow(
                deviceId.toString(), authorization.userId(), authorization.deviceName(), now, null, now));
        TokenPair tokens = identityService.issueTokens(UUID.fromString(authorization.userId()), "plugin:" + deviceId);
        return new DeviceTokenResponse("APPROVED", deviceId, tokens);
    }

    public List<AuthorizedDeviceRow> list(UUID userId) {
        return mapper.listDevices(userId.toString());
    }

    @Transactional
    public void revoke(UUID userId, UUID deviceId) {
        Instant now = Instant.now();
        if (mapper.revokeDevice(deviceId.toString(), userId.toString(), now) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "设备不存在或已撤销");
        }
        refreshTokenMapper.revokeAudience(userId.toString(), "plugin:" + deviceId, now);
    }

    private String uniqueUserCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = SecureTokens.userCode();
            if (mapper.findByUserCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique device user code");
    }

    private String normalizeUserCode(String userCode) {
        String compact = userCode.trim().toUpperCase(Locale.ROOT).replace("-", "");
        return compact.length() == 8 ? compact.substring(0, 4) + "-" + compact.substring(4) : userCode.trim().toUpperCase(Locale.ROOT);
    }

    public record DeviceCodeResponse(String deviceCode, String userCode, Instant expiresAt, int pollIntervalSeconds) {
    }

    public record DeviceTokenResponse(String status, UUID deviceId, TokenPair tokens) {
    }
}
