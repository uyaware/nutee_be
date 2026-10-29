package com.nutee.backend.auth.service;

/** Danh tính đã được provider xác minh; subject là mã người dùng ổn định bên provider. */
public record SocialIdentity(String subject, String email, String name, String picture) {
}
