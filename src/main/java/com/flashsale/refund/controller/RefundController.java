package com.flashsale.refund.controller;

import com.flashsale.refund.domain.RefundStatus;
import com.flashsale.refund.dto.RefundRequest;
import com.flashsale.refund.dto.RefundResponse;
import com.flashsale.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping("/refunds")
    public ResponseEntity<RefundResponse> requestRefund(@RequestBody RefundRequest request) {
        RefundResponse response = refundService.processRefund(request);

        HttpStatus status = response.status() == RefundStatus.UNKNOWN
                ? HttpStatus.ACCEPTED    // 202: 처리 중
                : HttpStatus.CREATED;    // 201: 완료

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/refunds/{refundId}")
    public ResponseEntity<RefundResponse> getRefund(@PathVariable Long refundId) {
        return ResponseEntity.ok(refundService.getRefund(refundId));
    }

    @GetMapping("/payments/{paymentId}/refunds")
    public ResponseEntity<List<RefundResponse>> getRefundsByPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(refundService.getRefundsByPayment(paymentId));
    }
}
