package dev.vedaaxis.api.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IdentityService identityService;

    public AuthController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/register")
    TokenPair register(@Valid @RequestBody Credentials request) {
        return identityService.register(request.email(), request.password());
    }

    @PostMapping("/login")
    TokenPair login(@Valid @RequestBody Credentials request) {
        return identityService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
        return identityService.refresh(request.refreshToken());
    }

    public record Credentials(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 10, max = 128) String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }
}
