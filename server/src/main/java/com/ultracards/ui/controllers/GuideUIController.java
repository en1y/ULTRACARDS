package com.ultracards.ui.controllers;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.server.entity.UserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Public game guides. The Briskula and Treseta guides run a small client-side
 * interactive walkthrough; poker and durak are placeholders until those games
 * ship. All routes fall under the public {@code anyRequest().permitAll()} rule.
 */
@Controller
public class GuideUIController {

    @GetMapping("/guides")
    public String hub(@AuthenticationPrincipal UserEntity user, Model model) {
        addUser(user, model);
        return "ui/guides/index";
    }

    @GetMapping("/guides/briskula")
    public String briskula(@AuthenticationPrincipal UserEntity user, Model model) {
        addUser(user, model);
        addFakePlayer(user, model);
        model.addAttribute("guideModes", BriskulaGameConfig.values());
        return "ui/guides/briskula";
    }

    @GetMapping("/guides/treseta")
    public String treseta(@AuthenticationPrincipal UserEntity user, Model model) {
        addUser(user, model);
        addFakePlayer(user, model);
        model.addAttribute("guideModes", TresetaGameConfig.values());
        return "ui/guides/treseta";
    }

    /**
     * The guide runs the real live-game renderer against a browser-only
     * simulator, so it needs a "self" id and name even for anonymous visitors.
     */
    private void addFakePlayer(UserEntity user, Model model) {
        model.addAttribute("currentUserId", user != null ? String.valueOf(user.getId()) : "1");
        model.addAttribute("username", user != null ? user.getUsername() : "You");
    }

    @GetMapping("/guides/poker")
    public String poker(@AuthenticationPrincipal UserEntity user, Model model) {
        addUser(user, model);
        model.addAttribute("gameName", "Poker");
        return "ui/guides/coming-soon";
    }

    @GetMapping("/guides/durak")
    public String durak(@AuthenticationPrincipal UserEntity user, Model model) {
        addUser(user, model);
        model.addAttribute("gameName", "Durak");
        return "ui/guides/coming-soon";
    }

    private void addUser(UserEntity user, Model model) {
        model.addAttribute("isAuthenticated", user != null);
        if (user != null) model.addAttribute("username", user.getUsername());
    }
}
