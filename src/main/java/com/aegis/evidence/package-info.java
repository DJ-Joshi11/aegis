/**
 * Evidence mapping, exposure aggregation & dashboard slice — owned by
 * Person C.
 *
 * Consumes FlaggedDecisions (and review-gap decisions), maps each one to a
 * broad insurance exposure category, records an audit evidence row, keeps a
 * non-binding per-agent exposure summary, and serves the evidence page plus
 * the main dashboard (the landing page that ties all three slices
 * together). Deliberately stops short of certifying compliance, making a
 * legal coverage determination, or setting a premium — those stay with
 * Guidewire's own systems and the carrier's compliance/underwriting teams.
 *
 * See README.md in this folder for full scope, and the "Person C" prompt
 * in the project doc / docs/CLAUDE_PROMPTS.md to get a Claude session
 * building this package.
 */
package com.aegis.evidence;
