package com.ultracards.server.repositories.games;

import com.ultracards.recorder.RecordedGame;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecordedGameRepository extends JpaRepository<RecordedGame, UUID> {
    @Query("select distinct g from RecordedGame g join g.players p where p.id = :userId and g.endedAt is not null")
    List<RecordedGame> findCompletedByUserId(@Param("userId") Long userId);

    Page<RecordedGame> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByEndedAtIsNull();

    long countByEndedAtIsNotNull();

    @Query(value = """
            select g from RecordedGame g
            where (:gameType is null
                    or (:gameType = 'BRISKULA' and type(g) = RecordedBriskulaGame)
                    or (:gameType = 'TRESETA' and type(g) = RecordedTresetaGame))
              and (:completed is null
                    or (:completed = true and g.endedAt is not null)
                    or (:completed = false and g.endedAt is null))
            """, countQuery = """
            select count(g) from RecordedGame g
            where (:gameType is null
                    or (:gameType = 'BRISKULA' and type(g) = RecordedBriskulaGame)
                    or (:gameType = 'TRESETA' and type(g) = RecordedTresetaGame))
              and (:completed is null
                    or (:completed = true and g.endedAt is not null)
                    or (:completed = false and g.endedAt is null))
            """)
    Page<RecordedGame> findAdminReport(@Param("gameType") String gameType,
                                       @Param("completed") Boolean completed,
                                       Pageable pageable);
}
