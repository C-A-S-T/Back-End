package com.example.cast.retrieval.repository;
import com.example.cast.retrieval.entity.RetrievalRun;
import org.springframework.data.jpa.repository.JpaRepository;
/** RetrievalRun 영속성 접근. 변경 정책은 서비스에서 수행한다. */
public interface RetrievalRunRepository extends JpaRepository<RetrievalRun, Long> {

}
