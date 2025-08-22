package com.ultracards.server.service;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.TokenDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.games.PlayerEntity;
import com.ultracards.server.repositories.games.briskula.BriskulaPlayerEntityRepository;
import com.ultracards.server.service.auth.TokenService;
import com.ultracards.server.service.auth.ValidationResult;
import com.ultracards.server.service.auth.VerificationCodeService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.List;

import static com.ultracards.server.enums.TokenValidationResultStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final BriskulaPlayerEntityRepository briskulaPlayerEntityRepository;
    @Value("${app.token.duration-minutes:15}")
    private int tokenDurationMinutes;

    private final VerificationCodeService verificationCodeService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final EmailService emailService;

    public String updateUsername (UsernameDTO username, String token, HttpServletResponse response) {
        var validatedToken = tokenService.validateToken(new TokenDTO(token));

        if (validatedToken.getStatus().equals(LOGOUT)) return null;
        if (validatedToken.getStatus().equals(ROTATED))
            processRotatedToken(validatedToken, response);

        var userEntity = validatedToken.getUser();
        userEntity.setUsername(username.getUsername());
        userEntity = userRepository.save(userEntity);
        return userEntity.getUsername();
    }

    public String getUsername (String token, HttpServletResponse response) {
        var validatedToken = tokenService.validateToken(new TokenDTO(token));

        if (validatedToken.getStatus().equals(LOGOUT)) return null;
        if (validatedToken.getStatus().equals(ROTATED))
            processRotatedToken(validatedToken, response);

        return validatedToken.getUser().getUsername();
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

    public void sendVerificationEmail(@Valid EmailDTO emailDTO, HttpServletResponse response) {

        var user = userService.getUserByEmail(emailDTO);
        var code = verificationCodeService.createVerificationCode(user);

        try {
            emailService.sendVerificationEmail(user, code);
            response.addCookie(new Cookie("token", tokenService.getTokenByUser(user).toString()));
        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}", emailDTO.getEmail(), e);
            throw new IllegalStateException("Failed to send verification email: " + e.getMessage(), e);
        } catch (UnsupportedEncodingException e) {
            log.error("Wrong file encoding was used to send verification email to {}", emailDTO.getEmail(), e);
            throw new IllegalStateException("Wrong file encoding was used to send verification email. " + e.getMessage(), e);
        }
    }

    public Boolean verifyCode(@Valid VerificationCodeDTO verificationCodeDTO, String token, HttpServletResponse response) {
        var validatedToken = tokenService.validateToken(new TokenDTO(token));

        if (validatedToken.getStatus().equals(LOGOUT)) return null;
        if (validatedToken.getStatus().equals(ROTATED))
            processRotatedToken(validatedToken, response);

        var user = validatedToken.getUser();
        var code = verificationCodeService.getVerificationCodeByUser(user);

        if (code == null) return null;

        return code.getCode().equals(verificationCodeDTO.getCode());
    }

    public ProfileDTO getProfile(String token, HttpServletResponse response) {
        var validatedToken = tokenService.validateToken(new TokenDTO(token));

        if (validatedToken.getStatus().equals(LOGOUT)) return null;
        if (validatedToken.getStatus().equals(ROTATED))
            processRotatedToken(validatedToken, response);

        var user = validatedToken.getUser();
        return createProfileByUser(user);
    }

    public ProfileDTO updateProfile(
            String token,
            @Valid ProfileDTO profileDTO,
            HttpServletResponse response) {

        var validatedToken = tokenService.validateToken(new TokenDTO(token));

        if (validatedToken.getStatus().equals(LOGOUT)) return null;
        if (validatedToken.getStatus().equals(ROTATED))
            processRotatedToken(validatedToken, response);

        var user = validatedToken.getUser();
        return updateProfileByUser(user, profileDTO);
    }

    private ProfileDTO updateProfileByUser(UserEntity user, @Valid ProfileDTO profileDTO) {
        user.setUsername(profileDTO.getUsername());
        user.setEmail(profileDTO.getEmail());
        userRepository.save(user);
        return profileDTO;
    }

    private void processRotatedToken(ValidationResult validationResult, HttpServletResponse response) {
        var cookie = new Cookie("token", validationResult.getToken().toString());

        cookie.setMaxAge(tokenDurationMinutes * 60);
        response.addCookie(cookie);
    }

    private ProfileDTO createProfileByUser(UserEntity user) {
        var profile = new ProfileDTO();

        profile.setUsername(user.getUsername());
        profile.setEmail(user.getEmail());

        var briskulaPlayers = briskulaPlayerEntityRepository.findByUser(user);
        profile.setBriskulaGamesPlayed(
                briskulaPlayers.size()
        );
        profile.setBriskulaGamesWon(
                countGamesWon(briskulaPlayers)
        );

        profile.setDurakGamesPlayed(0);
        profile.setDurakGamesWon(0);

        profile.setPokerGamesPlayed(0);
        profile.setPokerGamesWon(0);

        profile.setTresetaGamesPlayed(0);
        profile.setTresetaGamesWon(0);

        return profile;
    }

    private int countGamesWon (List<? extends PlayerEntity> players) {
        var gamesWon = 0;
        for (var player : players) {
            if (player.isWinner()) gamesWon++;
        }
        return gamesWon;
    }
}
