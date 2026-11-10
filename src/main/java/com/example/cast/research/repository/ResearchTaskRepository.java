package com.example.cast.research.repository;
import com.example.cast.research.entity.ResearchTask;
import org.springframework.data.jpa.repository.JpaRepository;
/** ResearchTask 영속성 접근. 변경 정책은 서비스에서 수행한다. */
public interface ResearchTaskRepository extends JpaRepository<ResearchTask, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from ResearchTask t where t.taskId = :id")
    java.util.Optional<ResearchTask> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
}
