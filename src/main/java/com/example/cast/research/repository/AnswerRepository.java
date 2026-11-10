package com.example.cast.research.repository;
import com.example.cast.research.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
/** Answer 영속성 접근. 변경 정책은 서비스에서 수행한다. */
public interface AnswerRepository extends JpaRepository<Answer, Long> {

    java.util.Optional<Answer> findByTaskTaskId(Long taskId);
}
