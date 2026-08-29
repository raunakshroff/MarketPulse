package com.marketpulse.stockdiscovery.repository;

import com.marketpulse.stockdiscovery.entity.EquityPrice;
import com.marketpulse.stockdiscovery.entity.EquityPriceId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquityPriceRepository extends JpaRepository<EquityPrice, EquityPriceId> {

    List<EquityPrice> findBySymbolOrderByTradeDateAsc(String symbol);
}
