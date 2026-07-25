package com.timbernest.auth;

public record AuthResponse(String token, Long userId, String email, String role, String name) {}
