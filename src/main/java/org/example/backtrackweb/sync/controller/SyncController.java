package org.example.backtrackweb.sync.controller;

import org.example.backtrackweb.sync.dto.SyncPayloads;
import org.example.backtrackweb.sync.security.DevicePrincipal;
import org.example.backtrackweb.sync.service.NumberBlockService;
import org.example.backtrackweb.sync.service.SyncLeaseService;
import org.example.backtrackweb.sync.service.SyncPullService;
import org.example.backtrackweb.sync.service.SyncPushService;
import org.example.backtrackweb.sync.entity.SyncDevice;
import org.example.backtrackweb.sync.service.DeviceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The synchronization API used by the Windows and Android clients.
 *
 * <p>Push sends commands, pull asks for everything that changed since the last
 * sequence number the device saw, and the lease endpoints decide which device
 * may write. Every one of them is scoped to the business of the calling device;
 * nothing accepts a business id from the caller.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncPushService pushService;
    private final SyncPullService pullService;
    private final SyncLeaseService leaseService;
    private final NumberBlockService numberBlockService;
    private final DeviceService deviceService;

    public SyncController(SyncPushService pushService,
                          SyncPullService pullService,
                          SyncLeaseService leaseService,
                          NumberBlockService numberBlockService,
                          DeviceService deviceService) {
        this.pushService = pushService;
        this.pullService = pullService;
        this.leaseService = leaseService;
        this.numberBlockService = numberBlockService;
        this.deviceService = deviceService;
    }

    @PostMapping("/push")
    public SyncPayloads.PushResponse push(@AuthenticationPrincipal DevicePrincipal principal,
                                          @RequestBody SyncPayloads.PushRequest request) {
        return pushService.push(principal, request);
    }

    @GetMapping("/pull")
    public SyncPayloads.PullResponse pull(@RequestParam(defaultValue = "0") long since,
                                          @RequestParam(defaultValue = "200") int limit) {
        return pullService.pull(since, limit);
    }

    @GetMapping("/lease")
    public SyncLeaseService.LeaseStatus lease() {
        return leaseService.status();
    }

    @PostMapping("/lease/claim")
    public SyncLeaseService.LeaseStatus claim(@AuthenticationPrincipal DevicePrincipal principal) {
        return leaseService.claim(principal);
    }

    @PostMapping("/lease/release")
    public SyncLeaseService.LeaseStatus release(@AuthenticationPrincipal DevicePrincipal principal) {
        return leaseService.release(principal);
    }

    public record HandoverRequest(String targetDeviceUuid, String reason) {
    }

    @PostMapping("/lease/handover")
    public SyncLeaseService.HandoverResult handover(@AuthenticationPrincipal DevicePrincipal principal,
                                                    @RequestBody HandoverRequest request) {
        return leaseService.handover(principal, request.targetDeviceUuid());
    }

    @PostMapping("/lease/force-handover")
    public SyncLeaseService.HandoverResult forceHandover(
            @AuthenticationPrincipal DevicePrincipal principal,
            @RequestBody HandoverRequest request) {
        return leaseService.forceHandover(principal, request.targetDeviceUuid(), request.reason());
    }

    @GetMapping("/lease/history")
    public List<Map<String, Object>> history() {
        return leaseService.history().stream().map(event -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("epoch", event.getEpoch());
            body.put("event", event.getEvent());
            body.put("deviceId", event.getDeviceId());
            body.put("previousDeviceId", event.getPreviousDeviceId());
            body.put("actorUserId", event.getActorUserId());
            body.put("note", event.getNote());
            body.put("createdAt", event.getCreatedAt().toString());
            return body;
        }).toList();
    }

    @PostMapping("/number-block")
    public NumberBlockService.NumberBlock numberBlock(@AuthenticationPrincipal DevicePrincipal principal,
                                                      @RequestBody(required = false)
                                                      SyncPayloads.NumberBlockRequest request) {
        SyncDevice device = deviceService.require(principal.deviceUuid());
        int count = request == null || request.count() == null ? 50 : request.count();
        return numberBlockService.issue(principal, device, count);
    }
}
