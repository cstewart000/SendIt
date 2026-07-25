package com.timbernest.user;

public record UserProfileDto(Long id, String email, String name, String contactDetails,
                             String defaultUnits, String role) {
    public static UserProfileDto from(AppUser u) {
        return new UserProfileDto(u.getId(), u.getEmail(), u.getName(),
                u.getContactDetails(), u.getDefaultUnits(), u.getRole());
    }
}
