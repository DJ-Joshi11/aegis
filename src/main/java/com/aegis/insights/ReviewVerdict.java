package com.aegis.insights;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A human reviewer's verdict on one AI-agent decision: did the decision
 * stand ({@code UPHELD}) or was it reversed ({@code OVERTURNED})?
 *
 * This is the only "ground truth" AEGIS ever sees about whether an agent
 * was right — so it's what turns an exposure signal into an error-rate
 * estimate. AEGIS records the verdict; it never makes it.
 */
@Entity
@Table(name = "review_verdict")
public class ReviewVerdict {

    public static final String UPHELD = "UPHELD";
    public static final String OVERTURNED = "OVERTURNED";

    @Id
    @Column(nullable = false, updatable = false, length = 100)
    private String decisionId;

    @Column(nullable = false, length = 20)
    private String verdict;

    /** Who made the call — a person's name/role, or "simulated-reviewer" for demo data. */
    @Column(nullable = false, length = 100)
    private String reviewer;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private Instant reviewedAt;

    protected ReviewVerdict() {
        // JPA
    }

    public ReviewVerdict(String decisionId, String verdict, String reviewer, String note, Instant reviewedAt) {
        this.decisionId = decisionId;
        this.verdict = verdict;
        this.reviewer = reviewer;
        this.note = note;
        this.reviewedAt = reviewedAt;
    }

    public String getDecisionId() {
        return decisionId;
    }

    public String getVerdict() {
        return verdict;
    }

    public String getReviewer() {
        return reviewer;
    }

    public String getNote() {
        return note;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public boolean isOverturned() {
        return OVERTURNED.equals(verdict);
    }
}
