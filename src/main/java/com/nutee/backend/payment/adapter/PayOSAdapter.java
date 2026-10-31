package com.nutee.backend.payment.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.payment.port.PaymentGateway;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;
import vn.payos.exception.PayOSException;
import vn.payos.exception.WebhookException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.PaymentLink;

/** Adapter duy nhất biết SDK payOS; chữ ký request/response và webhook do SDK chính thức xử lý. */
@Component @EnableConfigurationProperties(PaymentProperties.class)
public class PayOSAdapter implements PaymentGateway {
    private final PaymentProperties properties;
    private final ObjectMapper json;
    private final PayOS client;

    public PayOSAdapter(PaymentProperties properties, ObjectMapper json) {
        this.properties = properties; this.json = json;
        if (properties.enabled() && (blank(properties.clientId()) || blank(properties.apiKey()) || blank(properties.checksumKey())))
            throw new IllegalArgumentException("PAYOS_CLIENT_ID, PAYOS_API_KEY and PAYOS_CHECKSUM_KEY are required when PAYOS_ENABLED=true.");
        URI frontend = URI.create(properties.frontendUrl());
        if (frontend.getHost() == null || !("https".equals(frontend.getScheme()) || ("http".equals(frontend.getScheme()) && "localhost".equals(frontend.getHost())))
                || frontend.getQuery() != null || frontend.getFragment() != null || frontend.getUserInfo() != null)
            throw new IllegalArgumentException("PAYMENT_FRONTEND_URL must be a trusted HTTPS origin (localhost HTTP is supported).");
        client = properties.enabled() ? new PayOS(ClientOptions.builder().clientId(properties.clientId()).apiKey(properties.apiKey())
                .checksumKey(properties.checksumKey()).timeoutMs(10000).maxRetries(0).logLevel(ClientOptions.LogLevel.NONE).build()) : null;
    }
    @Override public boolean enabled() { return properties.enabled(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private void requireEnabled() { if (!enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Thanh toán payOS chưa được cấu hình."); }

    /** Mã orderCode ổn định cho phép khôi phục link khi request trước đã thành công nhưng mất response. */
    @Override public Link create(Request request) {
        requireEnabled();
        try {
            var response = client.paymentRequests().create(CreatePaymentLinkRequest.builder().orderCode(request.orderCode()).amount(request.amount())
                    .description("N" + Long.toString(request.orderCode(), 36).toUpperCase(java.util.Locale.ROOT))
                    .returnUrl(request.returnUrl()).cancelUrl(request.cancelUrl()).expiredAt(request.expiresAt().getEpochSecond()).build());
            if (response.getOrderCode() == null || response.getAmount() == null || response.getOrderCode() != request.orderCode() || response.getAmount() != request.amount() || !"VND".equals(response.getCurrency()) || !validId(response.getPaymentLinkId())) throw invalidProvider();
            URI url = URI.create(response.getCheckoutUrl());
            if (!"https".equals(url.getScheme()) || !"pay.payos.vn".equals(url.getHost()) || url.getUserInfo() != null) throw invalidProvider();
            return new Link(response.getOrderCode(), response.getAmount(), response.getCurrency(), response.getPaymentLinkId(), response.getCheckoutUrl());
        } catch (PayOSException exception) {
            // The same code is retried, never a new payment attempt after an uncertain response.
            var known = retrieve(request.orderCode());
            if (known.amount() != request.amount()) throw invalidProvider();
            return new Link(known.orderCode(), known.amount(), "VND", known.externalId(), "https://pay.payos.vn/web/" + known.externalId());
        } catch (IllegalArgumentException | NullPointerException exception) { throw invalidProvider(); }
    }
    @Override public State retrieve(long orderCode) {
        requireEnabled();
        try { return state(client.paymentRequests().get(orderCode), orderCode); }
        catch (PayOSException exception) { throw new ApiException(HttpStatus.BAD_GATEWAY, "Chưa thể kiểm tra thanh toán với payOS. Vui lòng thử lại."); }
    }
    @Override public State cancel(long orderCode) {
        requireEnabled();
        try { return state(client.paymentRequests().cancel(orderCode, "Customer cancelled or payment expired"), orderCode); }
        catch (PayOSException exception) { return retrieve(orderCode); }
    }
    private State state(PaymentLink value, long orderCode) {
        if (value == null || value.getOrderCode() == null || value.getOrderCode() != orderCode
                || value.getAmount() == null || value.getAmount() <= 0 || value.getAmountPaid() == null
                || value.getAmountPaid() < 0 || value.getStatus() == null || !validId(value.getId())) throw invalidProvider();
        return new State(value.getOrderCode(), value.getAmount(), value.getAmountPaid(), value.getId(), value.getStatus().toString());
    }
    private boolean validId(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,100}"); }
    @Override public Event verify(String rawBody) {
        requireEnabled();
        try {
            var body = json.readValue(rawBody, java.util.Map.class);
            var verified = client.webhooks().verify(body);
            if (verified.getReference() == null || verified.getReference().isBlank() || verified.getReference().length() > 200
                    || verified.getPaymentLinkId() == null || !verified.getPaymentLinkId().matches("[A-Za-z0-9_-]{1,100}")
                    || verified.getAmount() == null || verified.getAmount() < 0 || verified.getOrderCode() == null
                    || verified.getCurrency() == null || !verified.getCurrency().matches("[A-Z]{3}")
                    || verified.getCode() == null || !verified.getCode().matches("[A-Za-z0-9_]{1,20}")) throw invalidWebhook();
            return new Event(verified.getOrderCode(), verified.getAmount(), verified.getCurrency(), verified.getPaymentLinkId(), verified.getReference(), verified.getCode());
        } catch (WebhookException | com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException | NullPointerException exception) { throw invalidWebhook(); }
    }
    private ApiException invalidWebhook() { return new ApiException(HttpStatus.BAD_REQUEST, "Webhook payOS hoặc chữ ký không hợp lệ."); }
    private ApiException invalidProvider() { return new ApiException(HttpStatus.BAD_GATEWAY, "Dữ liệu thanh toán payOS không khớp với đơn hàng."); }
}
