package com.octapoint.core.anchor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerAnchorRepository extends JpaRepository<LedgerAnchor, String> {

    List<LedgerAnchor> findTop20ByOrderByCreatedAtDesc();
}
