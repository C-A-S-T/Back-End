package com.example.cast.persistence;
import com.example.cast.user.entity.*;
import com.example.cast.research.entity.*;
import com.example.cast.claim.entity.*;
import com.example.cast.document.entity.*;
import com.example.cast.document.dto.SourceSnapshot;
import com.example.cast.source.entity.*;
import com.example.cast.claim.service.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 백엔드 엔티티/기존 업무 제약을 제한된 cast_app 계정에서 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class CastPersistenceIntegrationTest extends TestDatabaseSupport {
    @PersistenceContext EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired ClaimService claims;
    @Autowired VerificationService verifications;
    @BeforeEach void assertRuntimeRole() throws Exception {
        try (var c=dataSource.getConnection(); var s=c.createStatement(); var rs=s.executeQuery("select current_user, current_schema()")) {
            assertEquals(postgres.getJdbcUrl(), c.getMetaData().getURL());
            assertTrue(rs.next()); assertEquals("cast_app",rs.getString(1)); assertEquals("backend",rs.getString(2));
        }
    }
    User user() { var u=new User(UUID.randomUUID()+"@example.invalid","hash");em.persist(u);return u; }
    ResearchTask task(User u) {
        var q=new ResearchQuery(u,"question");em.persist(q);var t=new ResearchTask(q);em.persist(t);return t;
    }
    Answer answer(ResearchTask t) { var a=new Answer(t);em.persist(a);return a; }
    Claim claim(Answer a) { var c=new Claim(a,0,"claim");em.persist(c);return c; }
    Document document() {
        var d=new Document(new SourceSnapshot(Math.abs(UUID.randomUUID().getMostSignificantBits()),"title",null,null,
                null,null,null,null,SourceType.OTHER,null));em.persist(d);return d;
    }
    void violates(String state,Runnable write) {
        var e=assertThrows(RuntimeException.class,()->{write.run();em.flush();});
        for(Throwable t=e;t!=null;t=t.getCause()) if(t instanceof java.sql.SQLException sql && state.equals(sql.getSQLState()))return;
        fail("Expected SQLSTATE "+state,e);
    }
    @Test void userAndAuditingPersist() {var u=user();em.flush();em.clear();var saved=em.find(User.class,u.getUserId());assertNotNull(saved.getCreatedAt());assertNotNull(saved.getUpdatedAt());}
    @Test void emailUnique() {var u=user();em.flush();violates("23505",()->em.persist(new User(u.getEmail(),"other")));}
    @Test void consentHistoryPreserved() {
        var u=user();em.persist(new UserConsent(u,ConsentType.TERMS,"v1",true,OffsetDateTime.now()));
        em.persist(new UserConsent(u,ConsentType.TERMS,"v1",false,OffsetDateTime.now()));em.flush();
        assertEquals(2L,em.createQuery("select count(c) from UserConsent c where c.user=:u",Long.class).setParameter("u",u).getSingleResult());
    }
    @Test void sourceValidityConstraint() {
        var d=document();em.flush();violates("23514",()->em.createNativeQuery("update backend.document set effective_from='2026-11-10',effective_to='2026-11-01' where document_id=:id").setParameter("id",d.getDocumentId()).executeUpdate());
    }
    @Test void taskPreviewAndState() {
        var t=task(user());t.start();t.updateProgress((short)3,"preview");em.flush();em.clear();
        var saved=em.find(ResearchTask.class,t.getTaskId());assertEquals("preview",saved.getPreviewSummary());assertEquals(ResearchTaskStatus.RUNNING,saved.getStatus());
    }
    @Test void oneAnswerPerTask() {var t=task(user());answer(t);em.flush();violates("23505",()->answer(t));}
    @Test void finalAnswerNeedsContent() {
        var a=answer(task(user()));assertThrows(IllegalArgumentException.class,()->a.markFinal(" ",null));em.flush();
        violates("23514",()->em.createNativeQuery("update backend.answer set answer_status='FINAL' where answer_id=:id").setParameter("id",a.getAnswerId()).executeUpdate());
    }
    @Test void claimIndexUnique() {var a=answer(task(user()));claim(a);em.flush();violates("23505",()->em.persist(new Claim(a,0,"duplicate")));}
    @Test void verificationAttemptUnique() {var c=claim(answer(task(user())));em.persist(new Verification(c,1));em.flush();violates("23505",()->em.persist(new Verification(c,1)));}
    @Test void completedVerificationImmutable() {
        var v=new Verification(claim(answer(task(user()))),1);v.complete(VerificationStatus.PARTIAL,"reason");
        assertThrows(IllegalStateException.class,()->v.complete(VerificationStatus.SUPPORTED,"other"));
    }
    @Test void externalRunDoesNotRequireAiRead() {
        var c=claim(answer(task(user())));var v=verifications.start(c.getClaimId(),987654L);em.flush();em.clear();
        assertEquals(987654L,em.find(Verification.class,v.getVerificationId()).getAiRunId());
    }
    @Test void claimCannotChangeAfterVerification() {
        var c=claim(answer(task(user())));
        var request=new com.example.cast.claim.dto.request.ClaimRequest("new",ClaimType.AMOUNT,Map.of("amount",300000));
        claims.revise(c.getClaimId(),request);verifications.start(c.getClaimId(),null);
        assertThrows(com.example.cast.global.exception.BusinessException.class,()->claims.revise(c.getClaimId(),request));
    }
    @Test void evidenceUsesSnapshotAndExternalChunkId() {
        var v=new Verification(claim(answer(task(user()))),1);em.persist(v);var d=document();
        var e=new Evidence(v,d,999999L,"quoted text",EvidenceRelation.SUPPORTS,1,2,"section");em.persist(e);em.flush();em.clear();
        var stored=em.find(Evidence.class,e.getEvidenceId());assertEquals("quoted text",stored.getEvidenceText());assertEquals(d.getDocumentId(),stored.getDocument().getDocumentId());
    }
    @Test void evidenceRelationCheck() {
        var v=new Verification(claim(answer(task(user()))),1);em.persist(v);
        var e=new Evidence(v,document(),null,"text",EvidenceRelation.NEUTRAL,null,null,null);em.persist(e);em.flush();
        violates("23514",()->em.createNativeQuery("update backend.evidence set evidence_relation='UNKNOWN' where evidence_id=:id").setParameter("id",e.getEvidenceId()).executeUpdate());
    }
    @Test void confidenceRejectsNaN() {
        var v=new Verification(claim(answer(task(user()))),1);em.persist(v);em.flush();
        violates("23514",()->em.createNativeQuery("update backend.verification set confidence='NaN'::float8 where verification_id=:id").setParameter("id",v.getVerificationId()).executeUpdate());
    }
    @Test void folderNameUniquePerUser() {var u=user();em.persist(new SourceFolder(u,"name"));em.flush();violates("23505",()->em.persist(new SourceFolder(u,"name")));}
    @Test void sourceUniquePerUser() {var u=user();var d=document();em.persist(SavedSource.manualSave(u,d));em.flush();violates("23505",()->em.persist(SavedSource.manualSave(u,d)));}
    @Test void anotherUsersFolderRejected() {
        var folder=new SourceFolder(user(),"folder");em.persist(folder);var saved=SavedSource.manualSave(user(),document());
        saved.moveToFolder(folder.getFolderId());violates("23503",()->em.persist(saved));
    }
    @Test void deletingFolderKeepsSavedDocument() {
        var u=user();var folder=new SourceFolder(u,"folder");em.persist(folder);var saved=SavedSource.manualSave(u,document());
        saved.moveToFolder(folder.getFolderId());em.persist(saved);em.flush();em.remove(folder);em.flush();em.clear();
        var stored=em.find(SavedSource.class,saved.getSavedSourceId());assertNull(stored.getFolderId());assertNotNull(stored.getDocument());
    }
    @Test void backendHasNoAiOrmEntities() {
        var names=em.getMetamodel().getEntities().stream().map(jakarta.persistence.metamodel.EntityType::getName).toList();
        assertFalse(names.contains("DocumentChunk"));assertFalse(names.contains("RetrievalRun"));
        assertFalse(names.contains("RetrievalResult"));assertFalse(names.contains("EvaluationCase"));
    }
}
