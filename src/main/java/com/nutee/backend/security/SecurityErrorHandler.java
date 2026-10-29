package com.nutee.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutee.backend.common.response.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** Trả lỗi xác thực, phân quyền và CSRF theo cùng định dạng ProblemDetail. */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** Trả lỗi 401 khi không có phiên hợp lệ, không lộ chi tiết xác minh JWT. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, "Authentication is required.");
    }

    /** Trả lỗi 403 và phân biệt CSRF hết hạn để frontend có thể lấy lại CSRF. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        if (exception instanceof CsrfException) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            var problem = ApiProblems.create(HttpStatus.FORBIDDEN, "CSRF token is missing or invalid.", request.getRequestURI());
            problem.setProperty("code", "CSRF_INVALID");
            objectMapper.writeValue(response.getOutputStream(), problem);
            return;
        }
        write(request, response, HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
    }

    /** Ghi lỗi HTTP thống nhất cùng đường dẫn và thời điểm xảy ra lỗi. */
    private void write(HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiProblems.create(status, detail, request.getRequestURI()));
    }
}
