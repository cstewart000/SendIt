package com.timbernest.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public UserProfileDto me(@AuthenticationPrincipal AppUser user) {
        return UserProfileDto.from(user);
    }

    @PatchMapping
    public UserProfileDto update(@AuthenticationPrincipal AppUser user,
                                 @RequestBody ProfileUpdateRequest req) {
        if (req.name() != null) user.setName(req.name());
        if (req.contactDetails() != null) user.setContactDetails(req.contactDetails());
        if (req.defaultUnits() != null) user.setDefaultUnits(req.defaultUnits());
        users.save(user);
        log.info("Updated profile userId={}", user.getId());
        return UserProfileDto.from(user);
    }
}
