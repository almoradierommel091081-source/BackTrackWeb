package org.example.backtrackweb.sync.controller;

import org.example.backtrackweb.sync.entity.SyncDevice;
import org.example.backtrackweb.sync.security.DevicePrincipal;
import org.example.backtrackweb.sync.service.DeviceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registering, listing and revoking the devices of a business.
 *
 * <p>Registration is the only endpoint that accepts a password, because a brand
 * new installation has no token yet. It returns the device token exactly once.
 * Every other endpoint uses the token and only ever sees its own business.
 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    public record RegisterRequest(String username, String password, String deviceName, String platform) {
    }

    public record RegisterResponse(String deviceUuid, String token, String tokenExpiresAt,
                                   Long businessId, String note) {
    }

    public record RevokeRequest(String reason) {
    }

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest request) {
        DeviceService.Registration registration = deviceService.register(request.username(),
                request.password(), request.deviceName(), request.platform());
        return new RegisterResponse(registration.deviceUuid(), registration.token(),
                registration.tokenExpiresAt().toString(), registration.businessId(),
                "Store this token safely. It is not shown again and cannot be recovered; "
                        + "only a hash is kept on the server.");
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return deviceService.list().stream().map(this::view).toList();
    }

    @PostMapping("/{deviceUuid}/revoke")
    public Map<String, Object> revoke(@PathVariable String deviceUuid,
                                      @RequestBody(required = false) RevokeRequest request,
                                      @AuthenticationPrincipal DevicePrincipal principal) {
        SyncDevice device = deviceService.revoke(deviceUuid,
                request == null ? null : request.reason(), principal);
        return view(device);
    }

    private Map<String, Object> view(SyncDevice device) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceUuid", device.getUuid());
        body.put("name", device.getName());
        body.put("platform", device.getPlatform());
        body.put("tokenPrefix", device.getTokenPrefix());
        body.put("createdAt", device.getCreatedAt().toString());
        body.put("tokenExpiresAt", device.getTokenExpiresAt().toString());
        body.put("lastSeenAt", device.getLastSeenAt() == null ? null : device.getLastSeenAt().toString());
        body.put("revokedAt", device.getRevokedAt() == null ? null : device.getRevokedAt().toString());
        body.put("revokedReason", device.getRevokedReason());
        // The token hash is deliberately not exposed; nothing here can be used to call the API.
        return body;
    }
}
