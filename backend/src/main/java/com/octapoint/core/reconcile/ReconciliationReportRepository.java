package com.octapoint.core.reconcile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReport, String> {

    List<ReconciliationReport> findTop20ByOrderByCreatedAtDesc();
}
