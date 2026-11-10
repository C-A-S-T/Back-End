package com.example.cast.persistence;
import com.example.cast.integration.ai.dto.*;
import com.example.cast.integration.ai.service.*;
import com.example.cast.claim.entity.*;
import com.example.cast.claim.dto.request.VerificationResultRequest;
import com.example.cast.document.dto.SourceSnapshot;
import com.example.cast.document.entity.SourceType;
import com.example.cast.research.entity.*;
import com.example.cast.user.entity.User;
import com.example.cast.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
/** 결과 상관관계/멱등성/동시 수신/트랜잭션 원자성을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AiHandoffIntegrationTest extends TestDatabaseSupport {
    @Autowired AiJobService jobs;
    @Autowired AiResultService results;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    Long task() {
        return new TransactionTemplate(manager).execute(tx->{
            var u=new User(UUID.randomUUID()+"@example.invalid","hash");em.persist(u);
            var q=new ResearchQuery(u,"test question");em.persist(q);
            var t=new ResearchTask(q);em.persist(t);return t.getTaskId();
        });
    }
    SourceSnapshot source(long id) {return new SourceSnapshot(id,"source",null,"https://example.invalid/source",null,null,null,"v1",SourceType.GOVERNMENT,null);}
    AiResultRequest success(UUID jobId,long sourceId) {
        var v=new VerificationResultRequest(VerificationStatus.SUPPORTED,"reason",VerificationMethod.RULE,VerificationStatus.SUPPORTED,null,0.9);
        var evidence=new AiResultRequest.Citation(source(sourceId),987L,"quote",EvidenceRelation.SUPPORTS,1,1,"section");
        var claim=new AiResultRequest.ClaimResult("amount",ClaimType.AMOUNT,Map.of("amount",300000,"unit","KRW"),v,123L,List.of(evidence));
        return new AiResultRequest(jobId,AiResultRequest.Outcome.SUCCESS,"answer","summary",null,List.of(claim));
    }
    long count(String table,Long taskId) {return jdbc.queryForObject("select count(*) from backend."+table+" where task_id=?",Long.class,taskId);}
    @Test void enqueueIsDurableAndIdempotent() {
        Long id=task();var first=jobs.enqueue(id);assertEquals(first,jobs.enqueue(id));
        assertTrue(jobs.pending().stream().anyMatch(j->j.jobId().equals(first.jobId())));
        assertEquals(1,count("ai_job",id));
    }
    @Test void successSavesCompleteResultAndDuplicateReturnsSameReceipt() {
        Long id=task();var job=jobs.enqueue(id);var result=success(job.jobId(),101L);
        var receipt=results.accept(id,result);assertEquals(ResearchTaskStatus.COMPLETED,receipt.status());
        assertEquals(receipt,results.accept(id,result));assertEquals(1,count("answer",id));
        assertEquals("quote",jdbc.queryForObject("select e.evidence_text from backend.evidence e join backend.verification v using(verification_id) join backend.claim c using(claim_id) where c.answer_id=?",String.class,receipt.answerId()));
    }
    @Test void wrongJobIdRejectedWithoutWrites() {
        Long id=task();jobs.enqueue(id);assertThrows(BusinessException.class,()->results.accept(id,success(UUID.randomUUID(),102L)));assertEquals(0,count("answer",id));
    }
    @Test void jobFromAnotherTaskRejected() {
        Long a=task(),b=task();var job=jobs.enqueue(a);jobs.enqueue(b);
        assertThrows(BusinessException.class,()->results.accept(b,success(job.jobId(),103L)));assertEquals(0,count("answer",b));
    }
    @Test void changedPayloadForSameJobConflicts() {
        Long id=task();var job=jobs.enqueue(id);var result=success(job.jobId(),104L);results.accept(id,result);
        var changed=new AiResultRequest(job.jobId(),result.outcome(),"different",result.summary(),null,result.claims());
        assertThrows(BusinessException.class,()->results.accept(id,changed));assertEquals(1,count("answer",id));
    }
    @Test void canceledTaskRejectsLateResult() {
        Long id=task();var job=jobs.enqueue(id);new TransactionTemplate(manager).executeWithoutResult(tx->em.find(ResearchTask.class,id).markCanceled());
        assertThrows(BusinessException.class,()->results.accept(id,success(job.jobId(),105L)));assertEquals(0,count("answer",id));
    }
    @Test void failureIsIdempotentAndDoesNotExposeAiInternalError() {
        Long id=task();var job=jobs.enqueue(id);
        var failure=new AiResultRequest(job.jobId(),AiResultRequest.Outcome.FAILURE,null,null,"internal-secret",List.of());
        var receipt=results.accept(id,failure);assertEquals(ResearchTaskStatus.FAILED,receipt.status());assertNull(receipt.answerId());
        assertEquals(receipt,results.accept(id,failure));
        assertFalse(jdbc.queryForObject("select error_message from backend.research_task where task_id=?",String.class,id).contains("internal-secret"));
    }
    @Test void invalidNestedResultRollsBackEverything() {
        Long id=task();var job=jobs.enqueue(id);var result=success(job.jobId(),106L);var claim=result.claims().getFirst();
        var invalid=new VerificationResultRequest(VerificationStatus.IN_PROGRESS,null,VerificationMethod.RULE,VerificationStatus.IN_PROGRESS,null,Double.NaN);
        var bad=new AiResultRequest(job.jobId(),result.outcome(),result.answer(),null,null,List.of(new AiResultRequest.ClaimResult(claim.content(),claim.type(),claim.value(),invalid,null,claim.citations())));
        assertThrows(jakarta.validation.ConstraintViolationException.class,()->results.accept(id,bad));assertEquals(0,count("answer",id));
        assertNull(jdbc.queryForObject("select result_hash from backend.ai_job where task_id=?",String.class,id));
    }
    @Test void mapOrderDoesNotChangeFingerprint() {
        Long id=task();var job=jobs.enqueue(id);var result=success(job.jobId(),107L);results.accept(id,result);
        var c=result.claims().getFirst();var reordered=new LinkedHashMap<String,Object>();reordered.put("unit","KRW");reordered.put("amount",300000);
        var retry=new AiResultRequest(job.jobId(),result.outcome(),result.answer(),result.summary(),null,List.of(new AiResultRequest.ClaimResult(c.content(),c.type(),reordered,c.verification(),c.aiRunId(),c.citations())));
        assertEquals(ResearchTaskStatus.COMPLETED,results.accept(id,retry).status());
    }
    @Test void concurrentDuplicateCallbacksProduceOneAnswer() throws Exception {
        Long id=task();var job=jobs.enqueue(id);var result=success(job.jobId(),108L);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<AiResultReceipt> submit=()->{assertTrue(start.await(10,TimeUnit.SECONDS));return results.accept(id,result);};
            var first=pool.submit(submit);var second=pool.submit(submit);start.countDown();
            assertEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));assertEquals(1,count("answer",id));
        }
    }
    @Test void concurrentTasksShareOnlyTheSourceSnapshot() throws Exception {
        Long a=task(),b=task();var ja=jobs.enqueue(a);var jb=jobs.enqueue(b);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->results.accept(a,success(ja.jobId(),109L)));
            var second=pool.submit(()->results.accept(b,success(jb.jobId(),109L)));
            assertNotEquals(first.get(20,TimeUnit.SECONDS).answerId(),second.get(20,TimeUnit.SECONDS).answerId());
            assertEquals(1,jdbc.queryForObject("select count(*) from backend.document where ai_document_id=109",Integer.class));
        }
    }
}
