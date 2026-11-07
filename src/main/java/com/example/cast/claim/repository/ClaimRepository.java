package com.example.cast.claim.repository;
import com.example.cast.claim.entity.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
/** Claim 영속성 접근. 변경 정책은 서비스에서 수행한다. */
public interface ClaimRepository extends JpaRepository<Claim, Long> {
    /** 내용 수정과 검증 시작을 직렬화하는 공통 잠금. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Claim c where c.claimId = :id")
    java.util.Optional<Claim> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
}
