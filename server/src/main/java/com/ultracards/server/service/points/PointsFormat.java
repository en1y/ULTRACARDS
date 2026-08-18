package com.ultracards.server.service.points;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Server-side twin of the browser's `pointsCompactAmount`: the same K/M/B shortening
 * so a page renders the number identically before and after its scripts run.
 */
@Component("pointsFormat")
public class PointsFormat {
    private static final String[] SUFFIXES = {"", "K", "M", "B", "T"};

    /** "1.5K" / "500" — the amount only, so callers can style the P themselves. */
    public String compact(Number value) {
        var amount = value == null ? 0 : value.longValue();
        var scaled = (double) Math.abs(amount);
        var tier = 0;
        while (scaled >= 1000 && tier < SUFFIXES.length - 1) {
            scaled /= 1000;
            tier++;
        }
        var rounded = Math.round(scaled * 10) / 10.0;
        var decimals = rounded == Math.rint(rounded) ? 0 : 1;
        return (amount < 0 ? "-" : "")
                + String.format(Locale.ROOT, "%,." + decimals + "f", rounded)
                + SUFFIXES[tier];
    }
}
