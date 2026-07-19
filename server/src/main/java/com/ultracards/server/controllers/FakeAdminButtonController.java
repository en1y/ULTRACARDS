package com.ultracards.server.controllers;

import com.ultracards.server.entity.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class FakeAdminButtonController {

    @PostMapping("/api/admin-mode/toggle")
    public void toggle(@AuthenticationPrincipal UserEntity user) {
        if (user == null || !user.isFakeAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
