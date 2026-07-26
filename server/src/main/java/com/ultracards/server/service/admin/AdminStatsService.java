package com.ultracards.server.service.admin;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.games.durak.DurakGameConfig;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.gateway.dto.admin.*;
import com.ultracards.recorder.*;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.gamestats.*;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.games.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminStatsService {
    private final UserRepository userRepository;
    private final UserGamesStatsRepository overallRepository;
    private final UserBriskulaStatsRepository briskulaRepository;
    private final UserTresetaStatsRepository tresetaRepository;
    private final UserDurakStatsRepository durakRepository;
    private final RecordedGameRepository recordedGameRepository;
    private final AdminAuditService auditService;

    @Transactional(readOnly = true)
    public AdminStatsDTO get(Long userId) {
        return snapshot(findUser(userId));
    }

    @Transactional
    public AdminStatsDiffDTO patch(UserEntity actor, Long userId, String gameTypeValue, String mode,
                                   AdminStatsPatchDTO patch) {
        requireReason(patch.reason());
        if (patch.played() == null && patch.wins() == null && patch.lastPlayedAt() == null)
            throw badRequest("Provide played, wins, or lastPlayedAt");
        var user = findUser(userId);
        var gameType = parseGameType(gameTypeValue);
        var normalizedMode = parseMode(gameType, mode);
        var current = storedLine(user, gameType, normalizedMode);
        var next = new AdminStatsPatchDTO(
                patch.played() == null ? current.getPlayed() : patch.played(),
                patch.wins() == null ? current.getWins() : patch.wins(),
                patch.lastPlayedAt() == null ? current.getLastPlayedAt() : patch.lastPlayedAt(),
                patch.reason(), patch.dryRun());
        if (next.played() < 0 || next.wins() < 0 || next.wins() > next.played())
            throw badRequest("Statistics require 0 <= wins <= played");
        var before = snapshot(user);
        var expected = calculate(userId, gameType);
        var expectedLine = expected.modes.get(normalizedMode);
        var expectedPlayed = expectedLine == null ? 0 : expectedLine.getPlayed();
        var expectedWins = expectedLine == null ? 0 : expectedLine.getWins();
        var warning = expectedPlayed != next.played() || expectedWins != next.wins()
                ? "Override differs from completed recorded-game history" : null;

        if (next.dryRun()) {
            return new AdminStatsDiffDTO(before, previewOverride(before, gameType, normalizedMode, next), warning, true);
        }

        if (gameType == GameType.BRISKULA) {
            var stats = briskulaRepository.findByUser(user).orElseGet(() -> briskulaRepository.save(new UserBriskulaStats(user)));
            var config = BriskulaGameConfig.valueOf(normalizedMode);
            stats.getConfigStats().put(config, new GameStats(next.played(), next.wins(), next.lastPlayedAt()));
            briskulaRepository.save(stats);
        } else if (gameType == GameType.DURAK) {
            var stats = durakRepository.findByUser(user).orElseGet(() -> durakRepository.save(new UserDurakStats(user)));
            stats.getConfigStats().put(normalizedMode, new GameStats(next.played(), next.wins(), next.lastPlayedAt()));
            durakRepository.save(stats);
        } else {
            var stats = tresetaRepository.findByUser(user).orElseGet(() -> tresetaRepository.save(new UserTresetaStats(user)));
            var config = TresetaGameConfig.valueOf(normalizedMode);
            stats.getConfigStats().put(config, new GameStats(next.played(), next.wins(), next.lastPlayedAt()));
            tresetaRepository.save(stats);
        }
        recomputeOverall(user, gameType);
        var after = snapshot(user);
        auditService.record(actor.getId(), "OVERRIDE_STATS", "USER_STATS", userId + ":" + gameType + ":" + mode,
                patch.reason(), "set played=" + next.played() + ", wins=" + next.wins(), "SUCCESS");
        return new AdminStatsDiffDTO(before, after, warning, false);
    }

    @Transactional
    public AdminStatsDiffDTO rebuild(UserEntity actor, Long userId, String gameTypeValue, String reason, boolean dryRun) {
        requireReason(reason);
        var user = findUser(userId);
        var gameType = gameTypeValue == null || gameTypeValue.isBlank() ? null : parseGameType(gameTypeValue);
        var before = snapshot(user);
        var briskula = gameType == null || gameType == GameType.BRISKULA ? calculate(userId, GameType.BRISKULA) : null;
        var treseta = gameType == null || gameType == GameType.TRESETA ? calculate(userId, GameType.TRESETA) : null;
        var durak = gameType == null || gameType == GameType.DURAK ? calculate(userId, GameType.DURAK) : null;
        var preview = previewRebuild(user, before, briskula, treseta, durak);
        if (dryRun) return new AdminStatsDiffDTO(before, preview, null, true);

        if (briskula != null) applyBriskula(user, briskula);
        if (treseta != null) applyTreseta(user, treseta);
        if (durak != null) applyDurak(user, durak);
        if (briskula != null) applyOverall(user, GameType.BRISKULA, briskula.total());
        if (treseta != null) applyOverall(user, GameType.TRESETA, treseta.total());
        if (durak != null) applyOverall(user, GameType.DURAK, durak.total());
        var after = snapshot(user);
        auditService.record(actor.getId(), "REBUILD_STATS", "USER_STATS", userId.toString(), reason,
                "rebuilt " + (gameType == null ? "all game types" : gameType.name()) + " from completed recordings", "SUCCESS");
        return new AdminStatsDiffDTO(before, after, null, false);
    }

    private Calculation calculate(Long userId, GameType type) {
        if (type == GameType.DURAK) return calculateDurak(userId);
        var result = new Calculation();
        for (var game : recordedGameRepository.findCompletedByUserId(userId)) {
            if (type == GameType.BRISKULA && !(game instanceof RecordedBriskulaGame)) continue;
            if (type == GameType.TRESETA && !(game instanceof RecordedTresetaGame)) continue;
            if (game instanceof RecordedDurakGame) continue;
            var points = points(game);
            if (points.isEmpty()) continue;
            var max = points.values().stream().mapToInt(Integer::intValue).max().orElse(Integer.MIN_VALUE);
            var winners = new HashSet<Long>();
            for (var entry : points.entrySet()) if (entry.getValue() == max) winners.add(entry.getKey());
            var won = winners.contains(userId);
            var mode = game instanceof RecordedBriskulaGame briskula ? briskula.gameConfig()
                    : ((RecordedTresetaGame) game).gameConfig();
            result.addMode(mode, won, game.endedAt());

            var teamIds = teamIds(game);
            var teammates = teammates(userId, teamIds);
            for (var player : game.players()) {
                if (player.id().equals(userId)) continue;
                var otherWon = winners.contains(player.id());
                if (teammates.contains(player.id())) result.addTeammate(mode, player.id(), won, game.endedAt());
                else if (won != otherWon) result.addOpponent(mode, player.id(), won, game.endedAt());
            }
        }
        return result;
    }

    /**
     * Durak outcomes are derived from the recorded loser and draw flags. A draw is a played game
     * for everybody and a win for nobody; two non-losers never count as having beaten each other.
     */
    private Calculation calculateDurak(Long userId) {
        var result = new Calculation();
        for (var game : recordedGameRepository.findCompletedByUserId(userId)) {
            if (!(game instanceof RecordedDurakGame durak)) continue;
            durak.requireValidResult();
            var mode = durak.modeKey();
            var won = !durak.draw() && !userId.equals(durak.loserUserId());
            result.addMode(mode, won, game.endedAt());
            for (var player : game.players()) {
                if (player.id().equals(userId)) continue;
                var beatThem = won && player.id().equals(durak.loserUserId());
                result.addOpponent(mode, player.id(), beatThem, game.endedAt());
            }
        }
        return result;
    }

    private Map<Long, Integer> points(RecordedGame game) {
        var points = new LinkedHashMap<Long, Integer>();
        for (var player : game.players()) points.put(player.id(), 0);
        for (var round : game.rounds()) {
            var winner = round.winner();
            if (winner == null) continue;
            var value = parsePoints(round.attributes().get("points"));
            points.merge(winner.id(), value, Integer::sum);
            for (var teammate : teammates(winner.id(), teamIds(game))) points.merge(teammate, value, Integer::sum);
        }
        return points;
    }

    private int parsePoints(String value) {
        try { return value == null ? 0 : Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private List<Long> teamIds(RecordedGame game) {
        if (game instanceof RecordedBriskulaGame briskula && briskula.teamsEnabled()) return briskula.teamUserIds();
        if (game instanceof RecordedTresetaGame treseta && treseta.teamsEnabled()) return treseta.teamUserIds();
        return List.of();
    }

    private Set<Long> teammates(Long userId, List<Long> ids) {
        var index = ids.indexOf(userId);
        if (index < 0) return Set.of();
        if (ids.size() == 4) return Set.of(ids.get(index < 2 ? 1 - index : 5 - index));
        var output = new HashSet<Long>(ids);
        output.remove(userId);
        return output;
    }

    private void applyBriskula(UserEntity user, Calculation calculation) {
        var stats = briskulaRepository.findByUser(user).orElseGet(() -> new UserBriskulaStats(user));
        var modes = new EnumMap<BriskulaGameConfig, GameStats>(BriskulaGameConfig.class);
        for (var entry : calculation.modes.entrySet()) modes.put(parseBriskulaMode(entry.getKey()), entry.getValue());
        stats.setConfigStats(modes);
        var opponents = new HashSet<BriskulaMatchupStats>();
        for (var entry : calculation.opponents.entrySet()) opponents.add(new BriskulaMatchupStats(
                entry.getKey().mode, entry.getKey().userId, entry.getValue().getPlayed(), entry.getValue().getWins(), entry.getValue().getLastPlayedAt()));
        var teammates = new HashSet<BriskulaMatchupStats>();
        for (var entry : calculation.teammates.entrySet()) teammates.add(new BriskulaMatchupStats(
                entry.getKey().mode, entry.getKey().userId, entry.getValue().getPlayed(), entry.getValue().getWins(), entry.getValue().getLastPlayedAt()));
        stats.setWinsAgainstUser(opponents);
        stats.setWinsWithTeammate(teammates);
        briskulaRepository.save(stats);
    }

    private void applyTreseta(UserEntity user, Calculation calculation) {
        var stats = tresetaRepository.findByUser(user).orElseGet(() -> new UserTresetaStats(user));
        var modes = new EnumMap<TresetaGameConfig, GameStats>(TresetaGameConfig.class);
        for (var entry : calculation.modes.entrySet()) modes.put(parseTresetaMode(entry.getKey()), entry.getValue());
        stats.setConfigStats(modes);
        var opponents = new HashSet<TresetaMatchupStats>();
        for (var entry : calculation.opponents.entrySet()) opponents.add(new TresetaMatchupStats(
                entry.getKey().mode, entry.getKey().userId, entry.getValue().getPlayed(), entry.getValue().getWins(), entry.getValue().getLastPlayedAt()));
        var teammates = new HashSet<TresetaMatchupStats>();
        for (var entry : calculation.teammates.entrySet()) teammates.add(new TresetaMatchupStats(
                entry.getKey().mode, entry.getKey().userId, entry.getValue().getPlayed(), entry.getValue().getWins(), entry.getValue().getLastPlayedAt()));
        stats.setWinsAgainstUser(opponents);
        stats.setWinsWithTeammate(teammates);
        tresetaRepository.save(stats);
    }

    private void applyDurak(UserEntity user, Calculation calculation) {
        var stats = durakRepository.findByUser(user).orElseGet(() -> new UserDurakStats(user));
        var modes = new LinkedHashMap<String, GameStats>();
        for (var entry : calculation.modes.entrySet()) modes.put(parseDurakMode(entry.getKey()), entry.getValue());
        stats.setConfigStats(modes);
        var opponents = new HashSet<DurakMatchupStats>();
        for (var entry : calculation.opponents.entrySet()) opponents.add(new DurakMatchupStats(
                entry.getKey().mode, entry.getKey().userId, entry.getValue().getPlayed(),
                entry.getValue().getWins(), entry.getValue().getLastPlayedAt()));
        stats.setWinsAgainstUser(opponents);
        // times-durak and draws are derived from the same recordings as the mode lines
        var timesDurak = 0;
        var draws = 0;
        for (var game : recordedGameRepository.findCompletedByUserId(user.getId())) {
            if (!(game instanceof RecordedDurakGame durak)) continue;
            if (durak.draw()) draws++;
            else if (user.getId().equals(durak.loserUserId())) timesDurak++;
        }
        stats.setTimesDurak(timesDurak);
        stats.setDraws(draws);
        durakRepository.save(stats);
    }

    private void recomputeOverall(UserEntity user, GameType type) {
        var lines = switch (type) {
            case BRISKULA -> briskulaRepository.findByUser(user).orElseThrow().getConfigStats().values();
            case DURAK -> durakRepository.findByUser(user).orElseThrow().getConfigStats().values();
            default -> tresetaRepository.findByUser(user).orElseThrow().getConfigStats().values();
        };
        var total = new GameStats();
        var played = 0;
        var wins = 0;
        Instant last = null;
        for (var line : lines) {
            played += line.getPlayed();
            wins += line.getWins();
            if (line.getLastPlayedAt() != null && (last == null || line.getLastPlayedAt().isAfter(last))) last = line.getLastPlayedAt();
        }
        total.setPlayed(played);
        total.setWins(wins);
        total.setLastPlayedAt(last);
        applyOverall(user, type, total);
    }

    private GameStats storedLine(UserEntity user, GameType type, String mode) {
        if (type == GameType.BRISKULA) {
            var config = BriskulaGameConfig.valueOf(mode);
            var line = briskulaRepository.findByUser(user).map(stats -> stats.getConfigStats().get(config))
                    .orElse(null);
            return line == null ? new GameStats() : line;
        }
        if (type == GameType.DURAK) {
            var line = durakRepository.findByUser(user).map(stats -> stats.getConfigStats().get(mode)).orElse(null);
            return line == null ? new GameStats() : line;
        }
        var config = TresetaGameConfig.valueOf(mode);
        var line = tresetaRepository.findByUser(user).map(stats -> stats.getConfigStats().get(config)).orElse(null);
        return line == null ? new GameStats() : line;
    }

    private void applyOverall(UserEntity user, GameType type, GameStats total) {
        var stats = overallRepository.findByUser(user).orElseGet(() -> new UserGamesStats(user));
        stats.getGameStats().put(type, total);
        overallRepository.save(stats);
    }

    private AdminStatsDTO snapshot(UserEntity user) {
        var overall = new LinkedHashMap<String, AdminStatLineDTO>();
        overallRepository.findByUser(user).ifPresent(stats -> stats.getGameStats().forEach((key, value) -> overall.put(key.name(), line(value))));
        var briskulaModes = new LinkedHashMap<String, AdminStatLineDTO>();
        var tresetaModes = new LinkedHashMap<String, AdminStatLineDTO>();
        var durakModes = new LinkedHashMap<String, AdminStatLineDTO>();
        var briskula = briskulaRepository.findByUser(user).orElse(null);
        var treseta = tresetaRepository.findByUser(user).orElse(null);
        var durak = durakRepository.findByUser(user).orElse(null);
        if (briskula != null) briskula.getConfigStats().forEach((key, value) -> briskulaModes.put(key.name(), line(value)));
        if (treseta != null) treseta.getConfigStats().forEach((key, value) -> tresetaModes.put(key.name(), line(value)));
        if (durak != null) durak.getConfigStats().forEach((key, value) -> durakModes.put(key, line(value)));
        return new AdminStatsDTO(user.getId(), overall, briskulaModes, tresetaModes, durakModes,
                briskula == null ? 0 : briskula.getWinsAgainstUser().size(),
                briskula == null ? 0 : briskula.getWinsWithTeammate().size(),
                treseta == null ? 0 : treseta.getWinsAgainstUser().size(),
                treseta == null ? 0 : treseta.getWinsWithTeammate().size(),
                durak == null ? 0 : durak.getWinsAgainstUser().size());
    }

    private AdminStatsDTO previewOverride(AdminStatsDTO before, GameType type, String mode, AdminStatsPatchDTO patch) {
        var briskula = new LinkedHashMap<>(before.briskulaModes());
        var treseta = new LinkedHashMap<>(before.tresetaModes());
        var durak = new LinkedHashMap<>(before.durakModes());
        var target = switch (type) {
            case BRISKULA -> briskula;
            case DURAK -> durak;
            default -> treseta;
        };
        target.put(mode.trim().toUpperCase(), new AdminStatLineDTO(patch.played(), patch.wins(), patch.lastPlayedAt()));
        var overall = new LinkedHashMap<>(before.overall());
        overall.put(type.name(), totalDto(target.values()));
        return new AdminStatsDTO(before.userId(), overall, briskula, treseta, durak, before.briskulaOpponentRows(),
                before.briskulaTeammateRows(), before.tresetaOpponentRows(), before.tresetaTeammateRows(),
                before.durakOpponentRows());
    }

    private AdminStatsDTO previewRebuild(UserEntity user, AdminStatsDTO before, Calculation briskula,
                                         Calculation treseta, Calculation durak) {
        var overall = new LinkedHashMap<>(before.overall());
        var briskulaModes = new LinkedHashMap<>(before.briskulaModes());
        var tresetaModes = new LinkedHashMap<>(before.tresetaModes());
        var durakModes = new LinkedHashMap<>(before.durakModes());
        var bo = before.briskulaOpponentRows(); var bt = before.briskulaTeammateRows();
        var to = before.tresetaOpponentRows(); var tt = before.tresetaTeammateRows();
        var duo = before.durakOpponentRows();
        if (briskula != null) {
            briskulaModes = toDtoMap(briskula.modes); overall.put(GameType.BRISKULA.name(), line(briskula.total()));
            bo = briskula.opponents.size(); bt = briskula.teammates.size();
        }
        if (treseta != null) {
            tresetaModes = toDtoMap(treseta.modes); overall.put(GameType.TRESETA.name(), line(treseta.total()));
            to = treseta.opponents.size(); tt = treseta.teammates.size();
        }
        if (durak != null) {
            durakModes = toDtoMap(durak.modes); overall.put(GameType.DURAK.name(), line(durak.total()));
            duo = durak.opponents.size();
        }
        return new AdminStatsDTO(user.getId(), overall, briskulaModes, tresetaModes, durakModes, bo, bt, to, tt, duo);
    }

    private LinkedHashMap<String, AdminStatLineDTO> toDtoMap(Map<String, GameStats> values) {
        var output = new LinkedHashMap<String, AdminStatLineDTO>();
        values.forEach((key, value) -> output.put(key, line(value)));
        return output;
    }

    private AdminStatLineDTO totalDto(Collection<AdminStatLineDTO> lines) {
        var played = 0; var wins = 0; Instant last = null;
        for (var line : lines) {
            played += line.played(); wins += line.wins();
            if (line.lastPlayedAt() != null && (last == null || line.lastPlayedAt().isAfter(last))) last = line.lastPlayedAt();
        }
        return new AdminStatLineDTO(played, wins, last);
    }

    private AdminStatLineDTO line(GameStats value) {
        return new AdminStatLineDTO(value.getPlayed(), value.getWins(), value.getLastPlayedAt());
    }

    private UserEntity findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private GameType parseGameType(String value) {
        try {
            var type = GameType.valueOf(value.trim().toUpperCase());
            if (type != GameType.BRISKULA && type != GameType.TRESETA && type != GameType.DURAK)
                throw badRequest("Only Briskula, Treseta and Durak have editable mode statistics");
            return type;
        } catch (ResponseStatusException ex) { throw ex; }
        catch (RuntimeException ex) { throw badRequest("Unknown game type: " + value); }
    }

    private BriskulaGameConfig parseBriskulaMode(String value) {
        try { return BriskulaGameConfig.valueOf(value.trim().toUpperCase()); }
        catch (RuntimeException ex) { throw badRequest("Unknown Briskula mode: " + value); }
    }

    private TresetaGameConfig parseTresetaMode(String value) {
        try { return TresetaGameConfig.valueOf(value.trim().toUpperCase()); }
        catch (RuntimeException ex) { throw badRequest("Unknown Treseta mode: " + value); }
    }

    private String parseDurakMode(String value) {
        try { return DurakGameConfig.fromModeKey(value.trim().toUpperCase()).modeKey(); }
        catch (RuntimeException ex) { throw badRequest("Unknown Durak mode: " + value); }
    }

    private String parseMode(GameType type, String value) {
        return switch (type) {
            case BRISKULA -> parseBriskulaMode(value).name();
            case DURAK -> parseDurakMode(value);
            default -> parseTresetaMode(value).name();
        };
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw badRequest("A nonblank reason is required");
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private record MatchupKey(String mode, Long userId) {}

    private static final class Calculation {
        private final Map<String, GameStats> modes = new LinkedHashMap<>();
        private final Map<MatchupKey, GameStats> opponents = new LinkedHashMap<>();
        private final Map<MatchupKey, GameStats> teammates = new LinkedHashMap<>();

        private void addMode(String mode, boolean won, Instant at) { add(modes, mode, won, at); }
        private void addOpponent(String mode, Long id, boolean won, Instant at) { add(opponents, new MatchupKey(mode, id), won, at); }
        private void addTeammate(String mode, Long id, boolean won, Instant at) { add(teammates, new MatchupKey(mode, id), won, at); }

        private <K> void add(Map<K, GameStats> map, K key, boolean won, Instant at) {
            var line = map.computeIfAbsent(key, ignored -> new GameStats());
            line.setPlayed(line.getPlayed() + 1);
            if (won) line.setWins(line.getWins() + 1);
            if (at != null && (line.getLastPlayedAt() == null || at.isAfter(line.getLastPlayedAt()))) line.setLastPlayedAt(at);
        }

        private GameStats total() {
            var total = new GameStats();
            for (var value : modes.values()) {
                total.setPlayed(total.getPlayed() + value.getPlayed());
                total.setWins(total.getWins() + value.getWins());
                if (value.getLastPlayedAt() != null && (total.getLastPlayedAt() == null || value.getLastPlayedAt().isAfter(total.getLastPlayedAt())))
                    total.setLastPlayedAt(value.getLastPlayedAt());
            }
            return total;
        }
    }
}
