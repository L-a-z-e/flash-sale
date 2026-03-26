package com.flashsale.queue.controller;

import com.flashsale.queue.dto.QueueEnterRequest;
import com.flashsale.queue.dto.QueuePositionResponse;
import com.flashsale.queue.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Queue", description = "대기열 API")
@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @Operation(summary = "대기열 진입")
    @PostMapping("/enter")
    public ResponseEntity<QueuePositionResponse> enter(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody QueueEnterRequest request) {

        QueuePositionResponse response = queueService.enter(userId, request.getTimeDealId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "대기열 순번 조회")
    @GetMapping("/position")
    public ResponseEntity<QueuePositionResponse> getPosition(
            @RequestParam Long timeDealId,
            @RequestParam Long userId) {

        QueuePositionResponse response = queueService.getPosition(userId, timeDealId);
        return ResponseEntity.ok(response);
    }
}
