package com.flashsale.purchase.controller;

import com.flashsale.purchase.dto.PurchaseRequest;
import com.flashsale.purchase.dto.PurchaseResponse;
import com.flashsale.purchase.service.PurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Purchase", description = "구매 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @Operation(summary = "타임딜 구매 요청")
    @PostMapping("/purchase")
    public ResponseEntity<PurchaseResponse> purchase(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Queue-Token", required = false) String queueToken,
            @RequestBody PurchaseRequest request) {

        PurchaseResponse response = purchaseService.purchase(userId, queueToken, request);
        return ResponseEntity.ok(response);
    }
}
