package com.marketpulse.refdata.repository;

import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.model.BackfillJobStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackfillJobRepository extends JpaRepository<BackfillJob, UUID> {

    /** The oldest job still PENDING or RUNNING, if any - used to reject a concurrent start. */
    Optional<BackfillJob> findFirstByStatusInOrderByCreatedAtAsc(Collection<BackfillJobStatus> statuses);

    /** All jobs in the given states - used at startup to reconcile jobs orphaned by a restart. */
    List<BackfillJob> findByStatusIn(Collection<BackfillJobStatus> statuses);

    List<BackfillJob> findTop50ByOrderByCreatedAtDesc();
}
