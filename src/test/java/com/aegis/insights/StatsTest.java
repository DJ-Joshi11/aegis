package com.aegis.insights;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsTest {

    @Test
    void wilsonIntervalMatchesTextbookValues() {
        double[] ci = Stats.wilson(5, 10, Stats.Z95);
        assertEquals(0.2366, ci[0], 0.0005);
        assertEquals(0.7634, ci[1], 0.0005);

        double[] empty = Stats.wilson(0, 0, Stats.Z95);
        assertEquals(0.0, empty[0], 1e-9);
        assertEquals(1.0, empty[1], 1e-9);

        double[] zero = Stats.wilson(0, 20, Stats.Z95);
        assertEquals(0.0, zero[0], 1e-9);
        assertTrue(zero[1] > 0.1 && zero[1] < 0.2, "upper bound stays informative when no events were seen");
    }

    @Test
    void twoProportionZTestUsesPooledStandardError() {
        // 30% vs 10% on 100 each: pooled 0.2, SE = sqrt(0.2*0.8*(2/100)) = 0.0566 → z = 3.54
        assertEquals(3.5355, Stats.twoProportionZ(30, 100, 10, 100), 0.001);
        assertEquals(-3.5355, Stats.twoProportionZ(10, 100, 30, 100), 0.001);
        assertEquals(0.0, Stats.twoProportionZ(0, 0, 3, 10), 1e-9);
    }

    @Test
    void normalCdfAndPValuesAreAccurate() {
        assertEquals(0.975, Stats.normalCdf(1.959964), 1e-4);
        assertEquals(0.5, Stats.normalCdf(0), 1e-6);
        assertEquals(0.05, Stats.pTwoSided(1.959964), 1e-4);
        assertEquals(0.025, Stats.pUpper(1.959964), 1e-4);
    }

    @Test
    void psiIsZeroForIdenticalMixesAndLargeForAShift() {
        assertEquals(0.0, Stats.psi(Map.of("A", 80, "B", 20), Map.of("A", 40, "B", 10)), 1e-3);
        assertTrue(Stats.psi(Map.of("A", 80, "B", 20), Map.of("A", 40, "B", 60)) > 0.25);
        // a category that only appears in one window is smoothed, not infinite
        assertTrue(Double.isFinite(Stats.psi(Map.of("A", 10), Map.of("B", 10))));
    }
}
