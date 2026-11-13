
package com.example.cast.retrieval.repository;

import com.example.cast.retrieval.entity.RetrievalResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RetrievalResultRepository
        extends JpaRepository<RetrievalResult, Long> {

    @Query("""
            SELECT r
            FROM RetrievalResult r
            JOIN FETCH r.chunk c
            JOIN FETCH c.document
            WHERE r.run.runId = :runId
            ORDER BY r.rank ASC
            """)
    List<RetrievalResult> findByRunIdWithChunk(
            @Param("runId") Long runId
    );
}
