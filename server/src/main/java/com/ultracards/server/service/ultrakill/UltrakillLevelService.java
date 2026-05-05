package com.ultracards.server.service.ultrakill;

import com.ultracards.config.UltrakillLevelsProperties;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class UltrakillLevelService {


    private static final Pattern LEVEL_PATTERN =
            Pattern.compile("\\b\\d+-\\d+\\b|\\bP-\\d+\\b|\\b\\d+-[SE]\\b");

    private final UltrakillLevelsProperties properties;
    private final Set<String> levels;
    private final Map<String, String> allLevels;

    public UltrakillLevelService(UltrakillLevelsProperties ultrakillLevelsProperties) {
        this.properties = ultrakillLevelsProperties;
        this.levels = new HashSet<>();
        this.allLevels = new HashMap<>();

        putAllLevels();
        putLevels();
    }

    public String[] findLevelNumbers(String message) {
        if (message == null || message.isEmpty()) return new String[0];

        var matcher = LEVEL_PATTERN.matcher(message.toUpperCase());
        var res = new LinkedHashSet<String>();
        while (matcher.find()) {
            var level = matcher.group();
            if (levels.contains(level)) {
                res.add(level);
            }
        }
        return res.toArray(new String[0]);
    }

    public String createMessage(String[] levelNumbersInMessage) {
        var res = new StringJoiner("\n");
        for (var levelNum: levelNumbersInMessage) {
            res.add(formatLevelTitle(levelNum));
            res.add(allLevels.get(levelNum));
            res.add("\n");
        }
        return res.toString();
    }

    public String formatLevelTitle(String levelNum) {
        var parts = levelNum.toUpperCase().split("-");
        var p1 = parts[0];
        var p2 = parts[1];
        return String.format("%s /// %s",
                properties.getLayers().getOrDefault(p1, p1),
                properties.getOrdinals().getOrDefault(p2, p2));
    }

    private void putLevels() {
        levels.addAll(properties.getPrelude().keySet());
        levels.addAll(properties.getAct1().keySet());
        levels.addAll(properties.getAct2().keySet());
        levels.addAll(properties.getAct3().keySet());
        levels.addAll(properties.getFinale().keySet());
        levels.addAll(properties.getPrimeSanctums().keySet());
        levels.addAll(properties.getSecretLevels().keySet());
        levels.addAll(properties.getEncores().keySet());
    }
    private void putAllLevels() {
        allLevels.putAll(properties.getPrelude());
        allLevels.putAll(properties.getAct1());
        allLevels.putAll(properties.getAct2());
        allLevels.putAll(properties.getAct3());
        allLevels.putAll(properties.getFinale());
        allLevels.putAll(properties.getPrimeSanctums());
        allLevels.putAll(properties.getSecretLevels());
        allLevels.putAll(properties.getEncores());
    }
}
