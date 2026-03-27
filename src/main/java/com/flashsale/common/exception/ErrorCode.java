package com.flashsale.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 재고
    STOCK_NOT_FOUND(HttpStatus.BAD_REQUEST, "STOCK_NOT_FOUND", "타임딜 재고 정보를 찾을 수 없습니다."),
    STOCK_INSUFFICIENT(HttpStatus.BAD_REQUEST, "STOCK_INSUFFICIENT", "재고가 부족합니다."),

    // Timedeal
    TIME_DEAL_NOT_FOUND(HttpStatus.BAD_REQUEST, "TIME_DEAL_NOT_FOUND", "타임딜을 찾을 수 없습니다."),

    // 대기열
    QUEUE_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, "QUEUE_TOKEN_REQUIRED", "입장 토큰이 필요합니다."),
    QUEUE_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "QUEUE_TOKEN_INVALID", "유효하지 않은 입장 토큰입니다."),

    // 환불
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다."),
    REFUND_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "REFUND_NOT_ALLOWED", "환불 불가 상태입니다."),
    REFUND_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "REFUND_AMOUNT_EXCEEDED", "환불 가능 금액을 초과했습니다."),
    REFUND_DUPLICATE(HttpStatus.CONFLICT, "REFUND_DUPLICATE", "이미 처리된 환불 요청입니다."),
    REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "환불 정보를 찾을 수 없습니다."),

    // 공통
    SERVICE_TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_TEMPORARILY_UNAVAILABLE", "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
