package com.timbernest.auth;

import com.timbernest.common.ApiException;
import com.timbernest.user.AppUser;
import com.timbernest.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        AppUser user = new AppUser();
        user.setEmail(req.email().trim().toLowerCase());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setName(req.name());
        user.setRole("USER");
        users.save(user);
        log.info("Registered user id={} email={}", user.getId(), user.getEmail());
        return toResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        AppUser user = users.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        log.info("Login ok userId={}", user.getId());
        return toResponse(user);
    }

    public String forgotPassword(String email) {
        log.info("Password reset requested for email={} (stub – no email sent)", email);
        return "If the account exists, a reset link would be emailed (stub in Phase 1).";
    }

    private AuthResponse toResponse(AppUser user) {
        String token = jwt.issue(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getRole(), user.getName());
    }
}
