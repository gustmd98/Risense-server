package com.risense.domain.checkin;

/** 작업 상태가 BLOCKED(막힘)일 때 필수 */
public enum BlockedReason {
    NO_TIME,             // 시간 부족
    NO_MATERIAL,         // 자료 부족
    NO_RESPONSE,         // 팀원 응답 없음
    TECH_ISSUE,          // 기술 문제
    UNCLEAR_REQUIREMENT, // 요구사항 불명확
    ETC
}
