package com.ultracards.server.service.games;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.gateway.dto.admin.AdminGameAvailabilityDTO;
import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameConfigDTO;
import com.ultracards.server.entity.games.GameAvailability;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.games.GameAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GameAvailabilityService {
    private static final String ALL_MODES = "*";
    private final GameAvailabilityRepository repository;

    @Transactional(readOnly = true)
    public List<AdminGameAvailabilityDTO> list() {
        var availability = values();
        var output = new ArrayList<AdminGameAvailabilityDTO>();
        for (var game : GameType.values()) {
            var gameEnabled = enabled(availability, game, ALL_MODES);
            output.add(new AdminGameAvailabilityDTO(game.name(), null, gameEnabled));
            for (var mode : modes(game))
                output.add(new AdminGameAvailabilityDTO(game.name(), mode, gameEnabled && enabled(availability, game, mode)));
        }
        return output;
    }

    @Transactional
    public AdminGameAvailabilityDTO setEnabled(String gameValue, String modeValue, boolean enabled) {
        var game = game(gameValue);
        var mode = mode(game, modeValue);
        var setting = repository.findByGameTypeAndMode(game, mode)
                .orElseGet(() -> new GameAvailability(game, mode, enabled));
        setting.setEnabled(enabled);
        repository.save(setting);
        return new AdminGameAvailabilityDTO(game.name(), ALL_MODES.equals(mode) ? null : mode, enabled);
    }

    @Transactional(readOnly = true)
    public void requireEnabled(GameTypeDTO gameType, GameConfigDTO config) {
        var game = GameType.fromDTO(gameType);
        var availability = values();
        if (!enabled(availability, game, ALL_MODES))
            throw unavailable(game, null);
        var mode = mode(gameType, config);
        if (mode != null && !enabled(availability, game, mode)) throw unavailable(game, mode);
    }

    private Map<String, Boolean> values() {
        var output = new HashMap<String, Boolean>();
        for (var setting : repository.findAll())
            output.put(key(setting.getGameType(), setting.getMode()), setting.isEnabled());
        return output;
    }

    private boolean enabled(Map<String, Boolean> values, GameType game, String mode) {
        return values.getOrDefault(key(game, mode), true);
    }

    private String key(GameType game, String mode) { return game.name() + ':' + mode; }

    private GameType game(String value) {
        try { return GameType.valueOf(value.trim().toUpperCase()); }
        catch (RuntimeException ex) { throw badRequest("Unknown game type: " + value); }
    }

    private String mode(GameType game, String value) {
        if (value == null || value.isBlank()) return ALL_MODES;
        var mode = value.trim().toUpperCase();
        if (!modes(game).contains(mode)) throw badRequest("Unknown " + game.name() + " mode: " + value);
        return mode;
    }

    private List<String> modes(GameType game) {
        return switch (game) {
            case BRISKULA -> java.util.Arrays.stream(BriskulaGameConfig.values()).map(Enum::name).toList();
            case TRESETA -> java.util.Arrays.stream(TresetaGameConfig.values()).map(Enum::name).toList();
            default -> List.of();
        };
    }

    private String mode(GameTypeDTO gameType, GameConfigDTO config) {
        if (config instanceof BriskulaGameConfigDTO briskula) {
            for (var mode : BriskulaGameConfig.values())
                if (mode.getNumberOfPlayers() == briskula.getNumberOfPlayers()
                        && mode.getCardsInHandNum() == briskula.getCardsInHandNum()
                        && mode.areTeamsEnabled() == briskula.getTeamsEnabled()) return mode.name();
        }
        if (config instanceof TresetaGameConfigDTO treseta) {
            for (var mode : TresetaGameConfig.values())
                if (mode.getNumberOfPlayers() == treseta.getNumberOfPlayers()
                        && mode.getCardsInHandNum() == treseta.getCardsInHandNum()
                        && mode.areTeamsEnabled() == treseta.getTeamsEnabled()) return mode.name();
        }
        return null;
    }

    private ResponseStatusException unavailable(GameType game, String mode) {
        var label = mode == null ? game.name() : game.name() + " / " + mode;
        return new ResponseStatusException(HttpStatus.CONFLICT, "Game availability is disabled for " + label);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
