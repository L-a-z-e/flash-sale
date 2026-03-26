package com.flashsale.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final RestClient restClient;

    public MockPaymentGateway(@Value("${payment.mock-pg.base-url}") String baseUrl) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(3))
                .withReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    @Override
    public PaymentApproveResult approve(PaymentApproveRequest request) {
        try {
            Map<String, Object> body = Map.of(
                    "orderId", request.getOrderId(),
                    "amount", request.getAmount(),
                    "orderName", request.getOrderName()
            );

            Map<String, Object> response = restClient.post()
                    .uri("/v1/payments/confirm")
                    .header("Idempotency-Key", request.getIdempotencyKey())
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.warn("Mock PG 결제 실패: status={}", res.getStatusCode());
                    })
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});

            if (response == null) {
                return failResult("EMPTY_RESPONSE", "PG 응답이 비어있습니다.");
            }

            String status = (String) response.get("status");
            if ("DONE".equals(status)) {
                return PaymentApproveResult.builder()
                        .success(true)
                        .pgPaymentKey((String) response.get("paymentKey"))
                        .pgStatus(status)
                        .method((String) response.get("method"))
                        .approvedAt(LocalDateTime.now())
                        .build();
            }

            return failResult(
                    (String) response.getOrDefault("code", "UNKNOWN"),
                    (String) response.getOrDefault("message", "결제 실패")
            );
        } catch (Exception e) {
            log.error("Mock PG 호출 중 예외: orderId={}", request.getOrderId(), e);
            return failResult("PG_CONNECTION_ERROR", e.getMessage());
        }
    }

    @Override
    public PaymentStatusResult getStatus(String pgPaymentKey) {
        Map<String, Object> response = restClient.get()
                .uri("/v1/payments/{paymentKey}", pgPaymentKey)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {});

        return PaymentStatusResult.builder()
                .pgPaymentKey(pgPaymentKey)
                .pgStatus((String) response.get("status"))
                .orderId((String) response.get("orderId"))
                .build();
    }

    @Override
    public PaymentStatusResult getStatusByOrderId(String orderId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/v1/payments/orders/{orderId}", orderId)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});

            return PaymentStatusResult.builder()
                    .pgPaymentKey((String) response.get("paymentKey"))
                    .pgStatus((String) response.get("status"))
                    .orderId(orderId)
                    .build();
        } catch (Exception e) {
            log.debug("PG에서 orderId={} 결제 내역 없음", orderId);
            return PaymentStatusResult.builder()
                    .pgPaymentKey(null)
                    .pgStatus("NOT_FOUND")
                    .orderId(orderId)
                    .build();
        }
    }

    @Override
    public PaymentCancelResult cancel(String pgPaymentKey, String reason) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", pgPaymentKey)
                    .body(Map.of("cancelReason", reason))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});

            return PaymentCancelResult.builder()
                    .success(true)
                    .pgPaymentKey(pgPaymentKey)
                    .pgStatus((String) response.get("status"))
                    .canceledAt(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Mock PG 취소 실패: paymentKey={}", pgPaymentKey, e);
            return PaymentCancelResult.builder()
                    .success(false)
                    .pgPaymentKey(pgPaymentKey)
                    .errorCode("PG_CANCEL_ERROR")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private PaymentApproveResult failResult(String errorCode, String errorMessage) {
        return PaymentApproveResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
