package com.ultracards.server.service;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.auth.TokenService;
import com.ultracards.server.service.auth.VerificationCodeService;
import com.ultracards.server.service.games.UserGamesStatsService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

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

    public String updateUsername (UsernameDTO username, TokenEntity token) {
        var userEntity = token.getUser();
        userEntity.setUsername(username.getUsername());
        userEntity = userRepository.save(userEntity);
        return userEntity.getUsername();
    }

    public String getUsername (TokenEntity token) {
        return token.getUser().getUsername();
    }

    public void logout(String token, HttpServletRequest request, HttpServletResponse response) {
        if (token != null) {
            tokenService.deleteTokenIfExists(token);
        }

        var cookies = request.getCookies();

        for (var c: cookies) {
            c.setValue("");
            c.setPath(c.getPath());
            c.setMaxAge(0);
            response.addCookie(c);
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
        profile.setRoles(user.getRoles().toString());
        profile.setId(user.getId());

        var gamesWon = userGamesStatsService.getByUser(user);

        if (gamesWon == null) {
            return profile;
        }

        profile.setBriskulaGamesPlayed(
                gamesWon.getGamesPlayed(GameType.BRISKULA));
        profile.setBriskulaGamesWon(
                gamesWon.getGamesWon(GameType.BRISKULA));

        profile.setDurakGamesPlayed(
                gamesWon.getGamesPlayed(GameType.DURAK));
        profile.setDurakGamesWon(
                gamesWon.getGamesWon(GameType.DURAK));

        profile.setTresetaGamesPlayed(
                gamesWon.getGamesPlayed(GameType.TRESETA));
        profile.setTresetaGamesWon(
                gamesWon.getGamesWon(GameType.TRESETA));

        profile.setPokerGamesPlayed(
                gamesWon.getGamesPlayed(GameType.POKER));
        profile.setPokerGamesWon(
                gamesWon.getGamesWon(GameType.POKER));

        return profile;
    }
}
