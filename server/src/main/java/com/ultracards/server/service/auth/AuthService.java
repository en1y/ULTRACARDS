package com.ultracards.server.service.auth;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UserSessionDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.UserSession;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.EmailService;
import com.ultracards.server.service.UserService;
import com.ultracards.server.service.games.UserGamesStatsService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final VerificationCodeService verificationCodeService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final UserGamesStatsService userGamesStatsService;
    private final SessionService sessionService;

    @Value("${app.cookie-token.same-site:Lax}")
    private String sameSite;
    @Value("${app.cookie-token.secure:false}")
    private boolean cookieSecure;
    @Value("${app.cookie-token.domain:}")
    private String cookieDomain;
    @Value("${app.token.update-privelege-duration-minutes:4}")
    private long updateDuration;

    public Boolean updateUsername(UserEntity user, @Valid UsernameDTO username, String token) {
        var session = sessionService.getSession(token);
        var createdAt = session.getLastAuthenticatedAt();
        if (createdAt.isBefore(createdAt.plusSeconds(updateDuration * 60))) {
            user.setUsername(username.getUsername());
            userRepository.save(user);
            return true;
        } else return false;
    }

    public String getUsername(UserEntity user) {
        return user.getUsername();
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var cookies = request.getCookies();

        for (var cookie : cookies) {
            var delCookie = new Cookie(cookie.getName(), null);
            delCookie.setMaxAge(0);
            delCookie.setPath("/");
            response.addCookie(delCookie);
        }
    }

    public void sendVerificationEmail(UserEntity user) {

        var code = verificationCodeService.createVerificationCode(user);

        try {
            emailService.sendVerificationEmail(user, code);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}", user.getEmail(), e);
            throw new IllegalStateException("Failed to send verification email: " + e.getMessage(), e);
        } catch (UnsupportedEncodingException e) {
            log.error("Wrong file encoding was used to send verification email to {}", user.getEmail(), e);
            throw new IllegalStateException("Wrong file encoding was used to send verification email. " + e.getMessage(), e);
        }
    }

    public Boolean verifyCode(@Valid VerificationCodeDTO verificationCodeDTO) {

        var code = verificationCodeService.getVerificationCodeByEmail(verificationCodeDTO.getEmail());

        if (code == null || !code.isValid()) return null;

        var res = code.getCode().equals(verificationCodeDTO.getCode());

        if (res) {
            verificationCodeService.setVerificationCodeUsed(code);
            userService.updateLastLogInDate(code.getUser());
        }

        return res;
    }

    public ProfileDTO getProfile(UserEntity user) {
        return createProfileByUser(user);
    }

    public Boolean updateProfile(
            UserEntity user,
            @Valid ProfileDTO profileDTO,
            String token) {
        return updateProfileByUser(user, profileDTO, token);
    }

    private Boolean updateProfileByUser(UserEntity user, @Valid ProfileDTO profileDTO, String token) {
        var session = sessionService.getSession(token);
        var createdAt = session.getLastAuthenticatedAt();
        if (createdAt.isBefore(createdAt.plusSeconds(updateDuration * 60))) {
            user.setUsername(profileDTO.getUsername());
            user.setEmail(profileDTO.getEmail());
            userRepository.save(user);
            return true;
        }
        return false;
    }

    private ProfileDTO createProfileByUser(UserEntity user) {
        var profile = new ProfileDTO();

        profile.setUsername(user.getUsername());
        profile.setEmail(user.getEmail());
        profile.setRoles(user.getRoles().stream().map(Enum::toString).collect(Collectors.toList()));
        profile.setId(user.getId());

        var gameStats = userGamesStatsService.getByUser(user);

        if (gameStats == null) {
            return profile;
        }
        var games = new HashMap<String, Integer[]>();
        games.put(GameType.BRISKULA.name(), new Integer[]{gameStats.getGamesPlayed(GameType.BRISKULA), gameStats.getGamesWon(GameType.BRISKULA)});
        games.put(GameType.DURAK.name(), new Integer[]{gameStats.getGamesPlayed(GameType.DURAK), gameStats.getGamesWon(GameType.DURAK)});
        games.put(GameType.TRESETA.name(), new Integer[]{gameStats.getGamesPlayed(GameType.TRESETA), gameStats.getGamesWon(GameType.TRESETA)});
        games.put(GameType.POKER.name(), new Integer[]{gameStats.getGamesPlayed(GameType.POKER), gameStats.getGamesWon(GameType.POKER)});

        profile.setPlayedAndWonGames(games);

        var gamesPlayed = 0;
        var gamesWon = 0;
        for (var arr: games.values()) {
            gamesPlayed += arr[0];
            gamesWon += arr[1];
        }
        profile.setGamesPlayed(gamesPlayed);
        profile.setGamesWon(gamesWon);

        return profile;
    }

    private void expireRefreshTokenCookie(HttpServletResponse response, String path) {
        var expiredRefreshToken = ResponseCookie.from("refreshToken", "")
                .path(path)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .maxAge(0);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            expiredRefreshToken.domain(cookieDomain);
        }

        response.addHeader("Set-Cookie", expiredRefreshToken.build().toString());
    }
}
