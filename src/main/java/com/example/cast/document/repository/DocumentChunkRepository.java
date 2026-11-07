package com.example.cast.document.repository;
import com.example.cast.document.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
/** DocumentChunk 영속성 접근. 변경 정책은 서비스에서 수행한다. */
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    @org.springframework.data.jpa.repository.Query("""
        select c from DocumentChunk c join fetch c.document d
        where (d.effectiveFrom is null or d.effectiveFrom <= :baseDate)
          and (d.effectiveTo is null or d.effectiveTo >= :baseDate)
        order by c.chunkId
        """)
    java.util.List<DocumentChunk> findValidOn(@org.springframework.data.repository.query.Param("baseDate") java.time.LocalDate baseDate);
}
