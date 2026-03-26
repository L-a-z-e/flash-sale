package com.flashsale.queue.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueuePositionResponse {

    private final long position;
    private final String status;
    private final String token;
}
