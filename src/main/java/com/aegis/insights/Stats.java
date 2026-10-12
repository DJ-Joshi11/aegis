package com.aegis.insights;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The small set of classical statistics the insights engine uses. Everything
 * here is textbook, closed-form and explainable to a regulator — no trained
 * model, no external library, no LLM.
 */
final class Stats {

    private Stats() {
    }

    /** z for a 95% two-sided confidence interval. */
    static final double Z95 = 1.959964;

    /**
     * Wilson score interval for a proportion k/n. Preferred over the naive
     * p ± z·sqrt(p(1-p)/n) because it stays inside [0,1] and behaves well
     * for small samples and rates near 0 or 1 — exactly our situation.
     *
     * @return {low, high}; {0, 1} when n == 0
     */
    static double[] wilson(int k, int n, double z) {
        if (n <= 0) {
            return new double[]{0.0, 1.0};
        }
        double p = (double) k / n;
        double z2 = z * z;
        double denom = 1 + z2 / n;
        double centre = (p + z2 / (2.0 * n)) / denom;
        double half = (z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n))) / denom;
        return new double[]{Math.max(0, centre - half), Math.min(1, centre + half)};
    }

    /**
     * Two-proportion z-test using the pooled standard error.
     * Positive z means group 1's rate is higher than group 2's.
     *
     * @return the z statistic, or 0 when either group is empty or the pooled rate is 0 or 1
     */
    static double twoProportionZ(int k1, int n1, int k2, int n2) {
        if (n1 <= 0 || n2 <= 0) {
            return 0.0;
        }
        double p1 = (double) k1 / n1;
        double p2 = (double) k2 / n2;
        double pooled = (double) (k1 + k2) / (n1 + n2);
        double se = Math.sqrt(pooled * (1 - pooled) * (1.0 / n1 + 1.0 / n2));
        return se == 0 ? 0.0 : (p1 - p2) / se;
    }

    /** Two-sided p-value for a standard-normal z statistic. */
    static double pTwoSided(double z) {
        return Math.min(1.0, 2 * (1 - normalCdf(Math.abs(z))));
    }

    /** One-sided (upper-tail) p-value for a standard-normal z statistic. */
    static double pUpper(double z) {
        return 1 - normalCdf(z);
    }

    /** Standard normal CDF via the Abramowitz–Stegun 7.1.26 erf approximation (|error| < 1.5e-7). */
    static double normalCdf(double z) {
        return 0.5 * (1 + erf(z / Math.sqrt(2)));
    }

    private static double erf(double x) {
        double sign = Math.signum(x);
        x = Math.abs(x);
        double t = 1 / (1 + 0.3275911 * x);
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return sign * y;
    }

    /**
     * Population Stability Index between two categorical distributions
     * (counts per category). Common rule of thumb in model-risk management:
     * &lt; 0.10 stable, 0.10–0.25 moderate shift, &gt; 0.25 significant shift.
     * Empty categories are smoothed so the log never blows up.
     */
    static double psi(Map<String, Integer> baseline, Map<String, Integer> recent) {
        int nb = baseline.values().stream().mapToInt(Integer::intValue).sum();
        int nr = recent.values().stream().mapToInt(Integer::intValue).sum();
        if (nb == 0 || nr == 0) {
            return 0.0;
        }
        Set<String> cats = new TreeSet<>(baseline.keySet());
        cats.addAll(recent.keySet());
        double eps = 0.5;
        double psi = 0;
        for (String c : cats) {
            double b = (baseline.getOrDefault(c, 0) + eps) / (nb + eps * cats.size());
            double r = (recent.getOrDefault(c, 0) + eps) / (nr + eps * cats.size());
            psi += (r - b) * Math.log(r / b);
        }
        return psi;
    }

    static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
