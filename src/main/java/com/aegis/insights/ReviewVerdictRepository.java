package com.aegis.insights;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewVerdictRepository extends JpaRepository<ReviewVerdict, String> {

    List<ReviewVerdict> findAllByOrderByReviewedAtDesc();
}
