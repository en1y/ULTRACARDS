package com.ultracards.ui.webui.controller;

import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.service.AuthService;
import com.ultracards.gateway.service.ClientTokenHolder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final AuthService authService;

    @GetMapping
    public String getProfile(
            @CookieValue("refreshToken") String token,
            HttpServletResponse response,
            Model model
    ) {
        setHeaderModel(token, response, model);
        var isAuthenticated = (Boolean) model.getAttribute("isAuthenticated");
        if (isAuthenticated == null || !isAuthenticated) {
            return "redirect:/";
        }

        var tokenHolder = new ClientTokenHolder(token);
        var profile = authService.getProfile(tokenHolder);
        model.addAttribute("profile", profile);
        response.addCookie(createCookie(tokenHolder));
        return "profile";
    }

    @PostMapping
    public String updateProfile(
            @CookieValue("refreshToken") String token,
            @Valid @ModelAttribute("profile") ProfileDTO profile,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model
    ) {
        setHeaderModel(token, response, model);
        var isAuthenticated = (Boolean) model.getAttribute("isAuthenticated");
        if (isAuthenticated == null || !isAuthenticated) {
            return "redirect:/";
        }

        if (bindingResult.hasErrors()) {
            // Return the same view with validation messages even though there are none currently :/
            return "profile";
        }

        var tokenHolder = new ClientTokenHolder(token);
        var newProfile = authService.updateProfile(tokenHolder, profile);
        response.addCookie(createCookie(tokenHolder));
        model.addAttribute("profile", newProfile);
        return "profile";
    }

    private void setHeaderModel(String refreshToken, HttpServletResponse response, Model model) {
        String username = null;
        if (refreshToken != null && !refreshToken.isBlank()) {
            var tokenHolder = new ClientTokenHolder(refreshToken);
            username = authService.getUsername(tokenHolder).getUsername();
            response.addCookie(createCookie(tokenHolder));
        }
        var auth = !(username == null);
        var name = (username != null && !username.isBlank()) ? username : "Bob.";
        model.addAttribute("isAuthenticated", auth);
        model.addAttribute("username", name);
        model.addAttribute("theme", "light");
    }

    private Cookie createCookie(ClientTokenHolder tokenHolder) {
        var cookie = new Cookie("refreshToken", tokenHolder.getToken());
        cookie.setPath("/");
        cookie.setMaxAge(60*60*24);
        cookie.setHttpOnly(true);
        return cookie;
    }

}
