package com.teamplay.riskradar.domain.project;

/** OVERDUE(마감일 지남)는 저장하지 않고 final_deadline < now 로 파생 */
public enum ProjectStatus {
    ACTIVE, COMPLETED
}
