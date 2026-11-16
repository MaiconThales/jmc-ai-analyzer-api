package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence;

import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.UUID;

public interface AnalysisSessionRepository extends JpaRepository<AnalysisSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AnalysisSession s where s.id = :sessionId")
    java.util.Optional<AnalysisSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AnalysisSession s
               set s.status = io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus.RUNNING,
                   s.startedAt = :startedAt
             where s.id = :sessionId
               and s.status = io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus.PENDING
            """)
    int transitionPendingToRunning(@Param("sessionId") UUID sessionId,
                                   @Param("startedAt") java.time.Instant startedAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AnalysisSession s
               set s.status = io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus.FAILED,
                   s.errorCount = 1,
                   s.finishedAt = :finishedAt
             where s.id = :sessionId
               and s.status = io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus.PENDING
            """)
    int transitionPendingToFailed(@Param("sessionId") UUID sessionId,
                                  @Param("finishedAt") java.time.Instant finishedAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AnalysisSession s
               set s.status = :status,
                   s.errorCount = :errorCount,
                   s.finishedAt = :finishedAt
             where s.id = :sessionId
               and s.status = io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus.RUNNING
            """)
    int transitionRunningToFinished(
            @Param("sessionId") UUID sessionId,
            @Param("status") io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus status,
            @Param("errorCount") int errorCount,
            @Param("finishedAt") java.time.Instant finishedAt);

    Page<AnalysisSession> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    default Page<AnalysisSession> findHistory(Pageable pageable) {
        return findAllByOrderByCreatedAtDescIdDesc(pageable);
    }
}
