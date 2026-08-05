package dev.vedaaxis.api.identity;

import dev.vedaaxis.api.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DeviceAuthorizationController {
    private final DeviceAuthorizationService service;

    public DeviceAuthorizationController(DeviceAuthorizationService service) {
        this.service = service;
    }

    @PostMapping("/device-authorizations")
    DeviceAuthorizationService.DeviceCodeResponse create(@Valid @RequestBody CreateRequest request) {
        return service.create(request.deviceName());
    }

    @PostMapping("/device-authorizations/{userCode}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void approve(@PathVariable String userCode) {
        service.approve(userCode, CurrentUser.id());
    }

    @PostMapping("/device-authorizations/token")
    DeviceAuthorizationService.DeviceTokenResponse poll(@Valid @RequestBody PollRequest request) {
        return service.poll(request.deviceCode());
    }

    @GetMapping("/devices")
    List<AuthorizedDeviceRow> list() {
        return service.list(CurrentUser.id());
    }

    @DeleteMapping("/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID deviceId) {
        service.revoke(CurrentUser.id(), deviceId);
    }

    public record CreateRequest(@NotBlank @Size(max = 120) String deviceName) {
    }

    public record PollRequest(@NotBlank String deviceCode) {
    }
}
