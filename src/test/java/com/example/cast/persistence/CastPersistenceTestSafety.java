package com.example.cast.persistence;
/** Docker 컨테이너도 별도 테스트 실행 승인을 받은 뒤에만 생성한다. */
final class CastPersistenceTestSafety {
    private CastPersistenceTestSafety() {}
    static void requireExplicitApproval() {
        if (!"YES".equals(System.getenv("CAST_PERSISTENCE_TEST_APPROVED"))) {
            throw new IllegalStateException("CAST_PERSISTENCE_TEST_APPROVED=YES requires explicit test-run approval.");
        }
    }
}
