package com.marketpulse.refdata.repository;

import com.marketpulse.refdata.entity.BackfillJobDate;
import com.marketpulse.refdata.entity.BackfillJobDateId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BackfillJobDateRepository extends JpaRepository<BackfillJobDate, BackfillJobDateId> {

    List<BackfillJobDate> findByJobIdOrderByTradeDateAsc(UUID jobId);

    /**
     * Dates any prior job found NOT_FOUND - i.e. known non-trading days.
     * Spans all jobs regardless of the owning job's own status: a holiday discovered by a
     * job that later failed or was interrupted is still a holiday.
     */
    @Query("""
            SELECT DISTINCT d.tradeDate FROM BackfillJobDate d
            WHERE d.status = com.marketpulse.refdata.model.BackfillDateStatus.NOT_FOUND
              AND d.tradeDate BETWEEN :from AND :to
            """)
    List<LocalDate> findKnownNonTradingDates(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
