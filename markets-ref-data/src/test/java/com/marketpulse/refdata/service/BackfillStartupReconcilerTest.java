package com.marketpulse.refdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.model.BackfillJobStatus;
import com.marketpulse.refdata.repository.BackfillJobRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BackfillStartupReconcilerTest {

    @Test
    void marksJobsOrphanedByARestartAsInterrupted() {
        BackfillJobRepository jobRepository = mock(BackfillJobRepository.class);
        BackfillJob orphan = new BackfillJob(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7), false, 5);
        orphan.markRunning();
        when(jobRepository.findByStatusIn(anyCollection())).thenReturn(List.of(orphan));

        new BackfillStartupReconciler(jobRepository).run(null);

        assertThat(orphan.getStatus()).isEqualTo(BackfillJobStatus.INTERRUPTED);
        assertThat(orphan.getMessage()).contains("restart");
        verify(jobRepository).save(orphan);

        ArgumentCaptor<Collection<BackfillJobStatus>> statusCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(jobRepository).findByStatusIn(statusCaptor.capture());
        assertThat(statusCaptor.getValue())
                .containsExactlyInAnyOrder(BackfillJobStatus.PENDING, BackfillJobStatus.RUNNING);
    }

    @Test
    void doesNothingWhenNoJobsWereLeftRunning() {
        BackfillJobRepository jobRepository = mock(BackfillJobRepository.class);
        when(jobRepository.findByStatusIn(anyCollection())).thenReturn(List.of());

        new BackfillStartupReconciler(jobRepository).run(null);

        verify(jobRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
