package com.marketpulse.refdata.repository;

import com.marketpulse.refdata.entity.EquityFundamentals;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquityFundamentalsRepository
        extends JpaRepository<EquityFundamentals, String>, EquityFundamentalsRepositoryCustom {
}
