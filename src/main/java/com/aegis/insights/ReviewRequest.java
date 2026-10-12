package com.aegis.insights;

/** Body of {@code POST /api/reviews/{decisionId}}. */
public record ReviewRequest(String verdict, String reviewer, String note) {
}
