package com.nutee.backend.common.response;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

public final class ApiProblems {

    private ApiProblems() {
    }

    public static ProblemDetail create(HttpStatusCode status, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return enrich(problem, path);
    }

    public static ProblemDetail enrich(ProblemDetail problem, String path) {
        problem.setInstance(URI.create(path));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
