/**
 * Detection & compliance slice — owned by Person B.
 *
 * Consumes DecisionEvents, flags anomalous ones, checks flagged decisions
 * against jurisdiction rules, and publishes FlaggedDecisions for the
 * payout package to consume.
 *
 * See README.md in this folder for full scope, and the "Person B" prompt
 * in the project doc / docs/CLAUDE_PROMPTS.md to get a Claude session
 * building this package.
 */
package com.aegis.detection;
