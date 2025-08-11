package com.ultracards.server.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/active")
public class ServerController {
    @GetMapping
    public ResponseEntity<Void> isServerActive() {
        return ResponseEntity.ok().build();
    }
}
