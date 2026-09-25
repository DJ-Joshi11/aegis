package com.aegis.payout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayoutAuditRepository extends JpaRepository<PayoutAudit, String> {

    List<PayoutAudit> findAllByOrderByTriggeredAtDesc();
}
