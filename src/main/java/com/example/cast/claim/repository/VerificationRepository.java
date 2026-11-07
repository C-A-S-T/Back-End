package com.example.cast.claim.repository;
import com.example.cast.claim.entity.Verification;
import org.springframework.data.jpa.repository.JpaRepository;
/** Verification 영속성 접근. 변경 정책은 서비스에서 수행한다. */
public interface VerificationRepository extends JpaRepository<Verification, Long> {
    boolean existsByClaimClaimId(Long claimId);
    @org.springframework.data.jpa.repository.Query("select coalesce(max(v.verAttemptNumber), 0) from Verification v where v.claim.claimId = :id")
    int latestAttempt(@org.springframework.data.repository.query.Param("id") Long claimId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from Verification v where v.verificationId = :id")
    java.util.Optional<Verification> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
}
