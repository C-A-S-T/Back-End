package com.example.cast.evaluation.entity;
import com.example.cast.claim.entity.ClaimType;
import java.util.Map;
/** 기존 문자열 정답은 content만 보존한다. type/value가 null이면 구조화 평가 전 보완해야 한다. */
public record ExpectedClaim(ClaimType type, String content, Map<String, Object> value) {}
