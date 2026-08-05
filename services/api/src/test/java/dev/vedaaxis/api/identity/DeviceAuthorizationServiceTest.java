package dev.vedaaxis.api.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DeviceAuthorizationServiceTest {
    @Autowired
    private DeviceAuthorizationService deviceAuthorizationService;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private UserMapper userMapper;

    @Test
    void approvedPluginDeviceCanExchangeItsCodeForTokens() {
        String email = "device-flow-" + UUID.randomUUID() + "@example.invalid";
        identityService.register(email, "VedaAxis-Device-Test-2026!");
        UUID userId = UUID.fromString(userMapper.findByEmail(email).orElseThrow().id());

        DeviceAuthorizationService.DeviceCodeResponse authorization =
                deviceAuthorizationService.create("Dalamud 插件");
        deviceAuthorizationService.approve(authorization.userCode(), userId);

        DeviceAuthorizationService.DeviceTokenResponse result =
                deviceAuthorizationService.poll(authorization.deviceCode());

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.deviceId()).isNotNull();
        assertThat(result.tokens()).isNotNull();
        assertThat(result.tokens().accessToken()).isNotBlank();
        assertThat(result.tokens().refreshToken()).isNotBlank();
    }
}
