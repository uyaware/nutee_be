package com.nutee.backend.common.response;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Tạo dữ liệu lỗi HTTP thống nhất cho filter bảo mật và controller. */
public final class ApiProblems {

    /** Không cho khởi tạo lớp tiện ích chỉ chứa hàm static. */
    private ApiProblems() {
    }

    /** Tạo ProblemDetail với status, thông báo và metadata của request. */
    public static ProblemDetail create(HttpStatusCode status, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return enrich(problem, path);
    }

    /** Bổ sung đường dẫn và thời điểm vào một lỗi HTTP. */
    public static ProblemDetail enrich(ProblemDetail problem, String path) {
        problem.setInstance(URI.create(path));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
