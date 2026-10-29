package com.nutee.backend.common.exception;

import com.nutee.backend.common.response.ApiProblems;
import com.nutee.backend.common.response.ValidationError;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Chuẩn hóa lỗi validation, nghiệp vụ và lỗi bất ngờ thành ProblemDetail. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Trả status và thông báo của lỗi nghiệp vụ đã được kiểm soát. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException exception, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        return handleExceptionInternal(exception, problem, new HttpHeaders(), exception.getStatus(), request);
    }

    /** Đổi lỗi ràng buộc dữ liệu thành 409 mà không lộ câu SQL. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleConflict(DataIntegrityViolationException exception, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The request conflicts with an existing record.");
        return handleExceptionInternal(exception, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    /** Trả danh sách trường không hợp lệ khi kiểm tra request body. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.add(new ValidationError(error.getField(), error.getDefaultMessage())));
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                errors.add(new ValidationError(error.getObjectName(), error.getDefaultMessage())));
        return validationResponse(exception, errors, headers, status, request);
    }

    /** Xử lý validation ở tham số controller, vẫn che lỗi phát sinh trong giá trị trả về. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (exception.isForReturnValue()) {
            return handleExceptionInternal(exception, null, headers, status, request);
        }
        List<ValidationError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error ->
                        new ValidationError(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return validationResponse(exception, errors, headers, status, request);
    }

    /** Trả lỗi 400 dễ hiểu khi JSON không đọc được. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Malformed request body.");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /** Chuyển lỗi constraint thành danh sách trường và thông báo. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException exception, WebRequest request) {
        List<ValidationError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ValidationError(violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        return validationResponse(exception, errors, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    /** Ghi log lỗi hệ thống và trả thông báo chung để tránh lộ chi tiết nội bộ. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        log.error("Unhandled request failure", exception);
        return handleExceptionInternal(exception, null, new HttpHeaders(),
                HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /** Trả 403 khi người dùng thiếu quyền thực hiện thao tác. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException exception, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource.");
        return handleExceptionInternal(exception, problem, new HttpHeaders(), HttpStatus.FORBIDDEN, request);
    }

    /** Trả 401 thống nhất khi thông tin xác thực không hợp lệ. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException exception, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Authentication is required.");
        return handleExceptionInternal(exception, problem, headers, HttpStatus.UNAUTHORIZED, request);
    }

    /** Thêm đường dẫn, thời điểm và che nội dung lỗi server trước khi trả response. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem;
        if (status.is5xxServerError() && !(exception instanceof ApiException)) {
            problem = ProblemDetail.forStatusAndDetail(status, "An unexpected error occurred.");
        } else if (body instanceof ProblemDetail detail) {
            problem = detail;
        } else {
            problem = ProblemDetail.forStatus(status);
        }
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        ApiProblems.enrich(problem, path);
        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }

    /** Dùng chung định dạng danh sách lỗi validation của các loại request. */
    private ResponseEntity<Object> validationResponse(
            Exception exception, List<ValidationError> errors, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }
}
