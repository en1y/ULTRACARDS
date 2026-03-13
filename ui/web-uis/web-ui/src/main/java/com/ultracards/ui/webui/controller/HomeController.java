package com.ultracards.ui.webui.controller;

import com.ultracards.gateway.service.AuthenticationService;
import com.ultracards.gateway.service.ClientTokenHolder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AuthenticationService authService;

    @GetMapping("/")
    public String index(
            @CookieValue(name = "refreshToken", required = false) String token,
            HttpServletResponse response,
            Model model
    ) {
        String username = null;

        if (token != null) {
            var tokenHolder = new ClientTokenHolder(token);
            username = authService.getUsername(tokenHolder).getUsername();
            response.addCookie(createCookie(tokenHolder));
        }

        ProfileController.setBasicModelAttributes(model, username);

        return "index";
    }

    private Cookie createCookie(ClientTokenHolder tokenHolder) {
        var cookie = new Cookie("refreshToken", tokenHolder.getToken());
        cookie.setPath("/");
        cookie.setMaxAge(60*60*24);
        cookie.setHttpOnly(true);
        return cookie;
    }

}

