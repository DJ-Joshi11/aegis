/**
 * Analytics layer — the statistical inference engine and the human review
 * loop. Sits beside the four-stage evidence pipeline, reads its outputs,
 * and never writes back into it.
 *
 * <ul>
 *   <li>{@link com.aegis.insights.InsightsEngine} — pure, testable statistics
 *   (peer outliers, drift, disparity, overturn rates, calibration, reasoning quality).</li>
 *   <li>{@link com.aegis.insights.ReviewService} — records human UPHELD / OVERTURNED verdicts.</li>
 *   <li>{@link com.aegis.insights.SimulatedReviewer} — demo stand-in for human reviewers.</li>
 * </ul>
 */
package com.aegis.insights;
