package com.nutee.backend.auth.service;

public record GoogleIdentity(String subject, String email, String name, String picture) {
}
