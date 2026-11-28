package org.example.backtrackweb.sync.controller;

import org.example.backtrackweb.entity.DiscrepancyStatus;
import org.example.backtrackweb.entity.InventoryDiscrepancy;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.repository.UserRepository;
import org.example.backtrackweb.service.InventoryDiscrepancyService;
import org.example.backtrackweb.sync.security.DevicePrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reviewing the stock shortages caused by sales that were completed offline.
 *
 * <p>Only an owner may resolve one. Nothing is deleted: resolving marks the row
 * and records who did it and why, so the full story of the sale stays available.
 */
@RestController
@RequestMapping("/api/v1/discrepancies")
public class DiscrepancyController {

    private final InventoryDiscrepancyService discrepancyService;
    private final UserRepository userRepository;

    public DiscrepancyController(InventoryDiscrepancyService discrepancyService,
                                 UserRepository userRepository) {
        this.discrepancyService = discrepancyService;
        this.userRepository = userRepository;
    }

    public record ResolveRequest(String note, String status) {
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String status) {
        DiscrepancyStatus filter = parseStatus(status);
        return discrepancyService.list(filter).stream().map(this::view).toList();
    }

    @PostMapping("/{id}/resolve")
    public Map<String, Object> resolve(@PathVariable Long id,
                                       @RequestBody(required = false) ResolveRequest request,
                                       @AuthenticationPrincipal DevicePrincipal principal) {
        if (!principal.isOwner()) {
            throw new BusinessException("Only the business owner can resolve an inventory discrepancy.");
        }
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new BusinessException("That account no longer exists."));
        DiscrepancyStatus target = request == null ? DiscrepancyStatus.RESOLVED : parseStatus(request.status());
        InventoryDiscrepancy resolved = discrepancyService.resolve(id,
                request == null ? null : request.note(),
                target == null ? DiscrepancyStatus.RESOLVED : target, user);
        return view(resolved);
    }

    private DiscrepancyStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DiscrepancyStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Unknown discrepancy status: " + status);
        }
    }

    private Map<String, Object> view(InventoryDiscrepancy discrepancy) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", discrepancy.getId());
        body.put("productUuid", discrepancy.getProduct().getUuid());
        body.put("productName", discrepancy.getProduct().getName());
        body.put("saleUuid", discrepancy.getSaleUuid());
        body.put("movementUuid", discrepancy.getMovementUuid());
        body.put("expectedStock", discrepancy.getExpectedStock());
        body.put("resultingStock", discrepancy.getResultingStock());
        body.put("delta", discrepancy.getDelta());
        body.put("status", discrepancy.getStatus().name());
        body.put("note", discrepancy.getNote());
        body.put("createdAt", discrepancy.getCreatedAt().toString());
        body.put("resolvedAt", discrepancy.getResolvedAt() == null ? null : discrepancy.getResolvedAt().toString());
        body.put("resolvedBy", discrepancy.getResolvedBy() == null
                ? null : discrepancy.getResolvedBy().getUsername());
        return body;
    }
}
