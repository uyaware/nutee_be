package com.nutee.backend.auth.service;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Gửi liên kết khôi phục bằng tài khoản Gmail đã cấp quyền gmail.send. */
@Service
public class GmailService {
    private static final String RESET_TEMPLATE = loadResetTemplate();
    private final RestClient client;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;

    @Autowired
    public GmailService(RestClient.Builder builder,
            @Value("${app.password-reset.gmail.client-id:}") String clientId,
            @Value("${app.password-reset.gmail.client-secret:}") String clientSecret,
            @Value("${app.password-reset.gmail.refresh-token:}") String refreshToken,
            @Value("${app.password-reset.enabled:false}") boolean enabled) {
        this(createClient(builder), clientId, clientSecret, refreshToken);
        if (enabled && (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank())) {
            throw new IllegalStateException("Gmail OAuth credentials are required when password reset is enabled.");
        }
    }

    GmailService(RestClient client, String clientId, String clientSecret, String refreshToken) {
        this.client = client;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
    }

    private static RestClient createClient(RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return builder.requestFactory(factory).build();
    }

    /** Trao đổi refresh token rồi gửi MIME message qua Gmail API. */
    public void sendPasswordReset(String recipient, String resetLink) {
        send(recipient, renderPasswordReset(resetLink, null));
    }

    /** Gửi mã OTP sáu chữ số cùng đường dẫn nhập mã, không ghi mã vào log. */
    public void sendPasswordResetOtp(String recipient, String resetLink, String otp) {
        if (otp == null || !otp.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("OTP must contain exactly six digits.");
        }
        send(recipient, renderPasswordReset(resetLink, otp));
    }

    static String renderPasswordReset(String resetLink, String otp) {
        String otpSection = otp == null ? "" : "<p style=\"margin:0 0 12px;font-size:15px;\">Mã xác nhận của bạn:</p>"
                + "<p style=\"margin:0 0 24px;padding:20px;background:#edf4ff;border-radius:8px;text-align:center;"
                + "font-size:32px;font-weight:bold;letter-spacing:8px;color:#0755d9;\">"
                + HtmlUtils.htmlEscape(otp) + "</p>";
        return RESET_TEMPLATE.replace("{{resetLink}}", HtmlUtils.htmlEscape(resetLink))
                .replace("{{otpSection}}", otpSection);
    }

    private static String loadResetTemplate() {
        try {
            return new ClassPathResource("mail/password-reset.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Password reset email template is unavailable.", exception);
        }
    }

    private void send(String recipient, String body) {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()
                || recipient == null || recipient.contains("\r") || recipient.contains("\n")) {
            throw new DeliveryException();
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        try {
            var token = client.post().uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(TokenResponse.class);
            if (token == null || token.access_token() == null || token.access_token().isBlank()) {
                throw new DeliveryException();
            }
            String subject = Base64.getEncoder().encodeToString("NUTEE - Đặt lại mật khẩu".getBytes(StandardCharsets.UTF_8));
            String encodedBody = Base64.getMimeEncoder(76, new byte[] {'\r', '\n'})
                    .encodeToString(body.getBytes(StandardCharsets.UTF_8));
            String mime = "To: " + recipient + "\r\nSubject: =?UTF-8?B?" + subject + "?=\r\n"
                    + "MIME-Version: 1.0\r\nContent-Type: text/html; charset=UTF-8\r\n"
                    + "Content-Transfer-Encoding: base64\r\n\r\n" + encodedBody + "\r\n";
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(mime.getBytes(StandardCharsets.UTF_8));
            client.post().uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                    .headers(headers -> headers.setBearerAuth(token.access_token()))
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("raw", raw))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new DeliveryException();
        }
    }

    private record TokenResponse(String access_token) {}

    /** Lỗi gửi thư không chứa token, nội dung thư hoặc phản hồi nhạy cảm từ Google. */
    public static class DeliveryException extends RuntimeException {}
}
