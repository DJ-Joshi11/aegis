/**
 * Payout, pricing & dashboard slice — owned by Person C.
 *
 * Consumes FlaggedDecisions, triggers parametric payouts, keeps a pricing
 * feedback signal, and serves the payout page plus the main dashboard
 * (the landing page that ties all three slices together).
 *
 * See README.md in this folder for full scope, and the "Person C" prompt
 * in the project doc / docs/CLAUDE_PROMPTS.md to get a Claude session
 * building this package.
 */
package com.aegis.payout;
