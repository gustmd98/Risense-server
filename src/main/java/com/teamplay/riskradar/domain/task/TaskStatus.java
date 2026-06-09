package com.teamplay.riskradar.domain.task;

/** 체크인 작업 업데이트도 이 enum을 재사용 (CANCELLED 제외하고 사용) */
public enum TaskStatus {
    TODO,         // 진행 전
    IN_PROGRESS,  // 진행 중
    BLOCKED,      // 막힘
    REVIEW,       // 검토 필요
    DONE,         // 완료
    CANCELLED     // 취소
}
