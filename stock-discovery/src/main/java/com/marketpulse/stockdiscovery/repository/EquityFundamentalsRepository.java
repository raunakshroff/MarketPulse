package com.marketpulse.stockdiscovery.repository;

import com.marketpulse.stockdiscovery.entity.EquityFundamentals;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquityFundamentalsRepository extends JpaRepository<EquityFundamentals, String> {
}
