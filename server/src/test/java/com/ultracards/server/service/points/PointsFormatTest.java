package com.ultracards.server.service.points;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The server-rendered amount has to match what points.js writes for the same number,
 * or a page visibly changes the moment its scripts run.
 */
class PointsFormatTest {
    private final PointsFormat format = new PointsFormat();

    @Test
    void shortensTheSameWayTheBrowserDoes() {
        assertThat(format.compact(0)).isEqualTo("0");
        assertThat(format.compact(500)).isEqualTo("500");
        assertThat(format.compact(1_000)).isEqualTo("1K");
        assertThat(format.compact(1_500)).isEqualTo("1.5K");
        assertThat(format.compact(25_000)).isEqualTo("25K");
        assertThat(format.compact(1_000_000)).isEqualTo("1M");
        assertThat(format.compact(2_500_000)).isEqualTo("2.5M");
        assertThat(format.compact(3_000_000_000L)).isEqualTo("3B");
        assertThat(format.compact(-1_500)).isEqualTo("-1.5K");
        assertThat(format.compact(null)).isEqualTo("0");
    }
}
