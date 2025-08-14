package com.ultracards.server.service;

import com.ultracards.server.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

public class GameNavigationInterceptor implements HandlerInterceptor {


    private static final Logger log = LoggerFactory.getLogger(GameNavigationInterceptor.class);
    private final UserRepository userRepository;

    public GameNavigationInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        try {
            var requestURI = request.getRequestURI();

            if (requestURI.startsWith("/js/") || requestURI.startsWith("/css/") || requestURI.startsWith("/error") || requestURI.endsWith("/logout") || requestURI.equals("/favicon.ico")) {
                return true;
            }

            var session = request.getSession(false);
            if (session == null) return true;

            var userId = (Long) session.getAttribute("userId");
            var token = (String) session.getAttribute("token");

            log.warn("Request URI: " + requestURI + " by user " + userId);

            var user = userRepository.findById(userId);

            if (user.isEmpty()) return true;


            // TODO: Work with the class, remove if needed
            /*if (isUserValid != null && !isUserValid) {
                clearCookies(request, response);
            }

            if (userId != null && token != null) {

                var games = gameService.listGamesByPlayer(userId, token);

                if (games.isEmpty()) return true;

                var gameRedirectId = request.getParameter("gameId");
                var isRedirectUrlIdNull = gameRedirectId == null;

                for (var game : games) {
                    var stillOnGamePage = requestURI.startsWith("/games/" + game.getGameId()) ||
                            (!isRedirectUrlIdNull && game.getGameId().equals(gameRedirectId));
                    if (!stillOnGamePage) {
                        log.error("Stopping game with id" + game.getGameId() + " because the request by user with id \"" + userId + "\" is: " + requestURI);
                        gameService.stopGame(game, token);
                    }
                }
            } */
            return true; // Continue with the request
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

}
