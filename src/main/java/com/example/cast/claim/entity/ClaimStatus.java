package com.example.cast.claim.entity;

public enum ClaimStatus {
    DRAFT,
    /** 검증 대상으로 확정됨. 근거의 지지는 VerificationStatus.SUPPORTED로 표현한다. */
    CONFIRMED,
    DISCARDED
}
