package com.marketpulse.refdata.repository;

import com.marketpulse.refdata.entity.EquitySymbol;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquitySymbolRepository
        extends JpaRepository<EquitySymbol, String>, EquitySymbolRepositoryCustom {
}
