package com.marketpulse.refdata.repository;

import com.marketpulse.refdata.entity.EquityPrice;
import com.marketpulse.refdata.entity.EquityPriceId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquityPriceRepository
        extends JpaRepository<EquityPrice, EquityPriceId>, EquityPriceRepositoryCustom {

    List<EquityPrice> findByTradeDateOrderBySymbolAsc(LocalDate tradeDate);
}
