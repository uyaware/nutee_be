package com.nutee.backend.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GmailServiceTest {
    @Test
    void rendersSixDigitOtpAndEscapesLinksInHtmlEmail() {
        String html = GmailService.renderPasswordReset("https://example.com/reset?email=a&next=b", "012345");
        assertThat(html).contains("012345", "15 phút", "email=a&amp;next=b")
                .doesNotContain("{{otpSection}}", "{{resetLink}}");
        assertThatThrownBy(() -> service.sendPasswordResetOtp("test@example.com", "https://example.com", "12345"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GmailService service = new GmailService(builder.build(), "client", "secret", "refresh");

    @Test
    void refreshesAccessTokenAndSendsEncodedMessage() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=refresh_token"),
                        org.hamcrest.Matchers.containsString("refresh_token=refresh"))))
                .andRespond(withSuccess("{\"access_token\":\"access\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access"))
                .andExpect(request -> {
                    var json = new ObjectMapper().readTree(((MockClientHttpRequest) request).getBodyAsBytes());
                    String mime = new String(Base64.getUrlDecoder().decode(json.get("raw").asText()), StandardCharsets.UTF_8);
                    assertThat(mime).contains("To: test@example.com\r\n", "Content-Transfer-Encoding: base64");
                    String body = new String(Base64.getMimeDecoder().decode(mime.split("\r\n\r\n", 2)[1]), StandardCharsets.UTF_8);
                    assertThat(body).contains("https://example.com/reset#token=abc", "đặt lại mật khẩu", "012345");
                })
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        service.sendPasswordResetOtp("test@example.com", "https://example.com/reset#token=abc", "012345");
        server.verify();
    }

    @Test
    void googleFailureIsReportedWithoutExposingResponse() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("sensitive response"));
        assertThatThrownBy(() -> service.sendPasswordReset("test@example.com", "https://example.com"))
                .isInstanceOf(GmailService.DeliveryException.class)
                .hasMessageNotContaining("sensitive response");
        server.verify();
    }
}
