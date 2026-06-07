package com.ultracards.server.service.users;

import com.ultracards.gateway.dto.auth.GameStatsDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UserGamesStatsDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.auth.SessionService;
import com.ultracards.server.service.games.UserGamesStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final UserGamesStatsService userGamesStatsService;

    @Value("${app.token.update-privilege-duration-minutes:4}")
    private long updateDuration;

    public ProfileDTO getProfile(UserEntity user) {
        return createProfileByUser(user);
    }

    public ProfileDTO getPublicProfile(Long userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        var profile = createProfileByUser(user);
        profile.setEmail(null);
        return profile;
    }

    public Boolean updateProfile(
            UserEntity user,
            @Valid ProfileDTO profileDTO,
            String token) {
        return updateProfileByUser(user, profileDTO, token);
    }

    private Boolean updateProfileByUser(UserEntity user, @Valid ProfileDTO profileDTO, String token) {
        var session = sessionService.getSession(token);
        var authenticatedAt = session.getLastAuthenticatedAt();
        if (Instant.now().isAfter(authenticatedAt.plusSeconds(updateDuration * 60)))
            return false;
        user.setUsername(profileDTO.getUsername());
        user.setEmail(profileDTO.getEmail());
        userRepository.save(user);
        return true;
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
        var games = new LinkedHashMap<String, GameStatsDTO>();
        for (var gameType : GameType.values()) {
            games.put(
                    gameType.name(),
                    new GameStatsDTO(
                            gameStats.getGamesPlayed(gameType),
                            gameStats.getGamesWon(gameType),
                            gameStats.getLastPlayedAt(gameType)
                    )
            );
        }

        profile.setUserGamesStats(new UserGamesStatsDTO(
                gameStats.getId(),
                user.getId(),
                games
        ));

        var gamesPlayed = 0;
        var gamesWon = 0;
        for (var stats: games.values()) {
            gamesPlayed += stats.getPlayed();
            gamesWon += stats.getWins();
        }
        profile.setGamesPlayed(gamesPlayed);
        profile.setGamesWon(gamesWon);

        return profile;
    }
}
