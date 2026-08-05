package dev.vedaaxis.api.security;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    @Test
    void issuesAndVerifiesSignedToken() {
        JwtService service = new JwtService(
                new ObjectMapper(),
                new SecurityProperties(
                        "test-secret-with-at-least-thirty-two-characters", 15, 30, 10));
        UUID userId = UUID.randomUUID();

        String token = service.issue(userId, "web", Duration.ofMinutes(5));
        JwtService.AuthenticatedUser user = service.verify(token);

        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.audience()).isEqualTo("web");
    }
}
