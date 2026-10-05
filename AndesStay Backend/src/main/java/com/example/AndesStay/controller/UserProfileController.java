package com.example.AndesStay.controller;

import com.example.AndesStay.service.UserProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class UserProfileController {
    public record Registration(String displayName) {}
    private final UserProfileService service;
    public UserProfileController(UserProfileService service) { this.service = service; }

    @GetMapping("/profile")
    public UserProfileService.Profile profile(@AuthenticationPrincipal Jwt jwt) { return service.get(jwt); }
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfileService.Profile register(@RequestBody Registration registration, @AuthenticationPrincipal Jwt jwt) {
        return service.register(registration.displayName(), jwt);
    }
}
