package com.flashsale.timedeal.controller;

import com.flashsale.timedeal.dto.TimeDealCreateRequest;
import com.flashsale.timedeal.dto.TimeDealResponse;
import com.flashsale.timedeal.service.TimeDealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - TimeDeal", description = "타임딜 관리 API")
@RestController
@RequestMapping("/api/v1/admin/time-deals")
@RequiredArgsConstructor
public class TimeDealAdminController {

    private final TimeDealService timeDealService;

    @Operation(summary = "타임딜 생성")
    @PostMapping
    public ResponseEntity<TimeDealResponse> create(@RequestBody TimeDealCreateRequest request) {
        TimeDealResponse response = timeDealService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "타임딜 조회")
    @GetMapping("/{id}")
    public ResponseEntity<TimeDealResponse> getById(@PathVariable Long id) {
        TimeDealResponse response = timeDealService.getById(id);
        return ResponseEntity.ok(response);
    }
}
