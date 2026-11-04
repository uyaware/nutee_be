package com.nutee.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Khai báo tài liệu API và cơ chế xác thực JWT qua cookie trong Swagger. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    /** Mô tả access JWT trong cookie và yêu cầu CSRF cho thao tác ghi dữ liệu. */
    @Bean
    OpenAPI nuteeOpenApi() {
        return new OpenAPI()
                .info(new Info().title("NUTEE API").version("v1")
                        .description("JWT authentication via HttpOnly cookies. Send X-XSRF-TOKEN for mutations."))
                .components(new Components().addSecuritySchemes("jwtAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("accessToken")
                        .description("Access JWT, automatically sent in the accessToken cookie.")))
                .addSecurityItem(new SecurityRequirement().addList("jwtAuth"));
    }
}
