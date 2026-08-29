package com.marketpulse.refdata.repository;

import com.marketpulse.refdata.entity.EquityPrice;
import com.marketpulse.refdata.entity.EquityPriceId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquityPriceRepository
        extends JpaRepository<EquityPrice, EquityPriceId>, EquityPriceRepositoryCustom {

    List<EquityPrice> findByTradeDateOrderBySymbolAsc(LocalDate tradeDate);

    /** Distinct dates already loaded in the range - the primary skip set for a backfill. */
    @Query("SELECT DISTINCT p.tradeDate FROM EquityPrice p WHERE p.tradeDate BETWEEN :from AND :to")
    List<LocalDate> findDistinctTradeDatesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
