package com.risense.domain.member;

public enum MemberStatus {
    PENDING,   // 승인 대기
    ACTIVE,    // 참여 중
    REJECTED,  // 거절됨
    LEFT,      // 나감
    REMOVED    // 내보내짐
}
