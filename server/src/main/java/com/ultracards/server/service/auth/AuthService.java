package com.ultracards.server.service.auth;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.TokenEntity;
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

    @Value("${app.cookie-token.same-site:Lax}")
    private String sameSite;

    @Value("${app.cookie-token.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie-token.domain:}")
    private String cookieDomain;

    public String updateUsername (UsernameDTO username, TokenEntity token) {
        var userEntity = token.getUser();
        userEntity.setUsername(username.getUsername());
        userEntity = userRepository.save(userEntity);
        return userEntity.getUsername();
    }

    public String getUsername (TokenEntity token) {
        return token.getUser().getUsername();
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

    public void sendVerificationEmail(@Valid EmailDTO emailDTO, TokenEntity token) {

        var user = token.getUser();
        var code = verificationCodeService.createVerificationCode(user);

        try {
            emailService.sendVerificationEmail(user, code);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}", emailDTO.getEmail(), e);
            throw new IllegalStateException("Failed to send verification email: " + e.getMessage(), e);
        } catch (UnsupportedEncodingException e) {
            log.error("Wrong file encoding was used to send verification email to {}", emailDTO.getEmail(), e);
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

    public ProfileDTO getProfile(TokenEntity token) {
        var user = token.getUser();
        return createProfileByUser(user);
    }

    public ProfileDTO updateProfile(
            @Valid ProfileDTO profileDTO,
            TokenEntity token
    ) {
        var user = token.getUser();
        return updateProfileByUser(user, profileDTO);
    }

    private ProfileDTO updateProfileByUser(UserEntity user, @Valid ProfileDTO profileDTO) {
        user.setUsername(profileDTO.getUsername());
        user.setEmail(profileDTO.getEmail());
        userRepository.save(user);
        return profileDTO;
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
