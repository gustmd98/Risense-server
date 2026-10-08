package com.risense.taskapi;

import com.risense.domain.task.*;
import java.time.OffsetDateTime;

public record AssigneeHistoryResponse(Long id, Long memberId, String nickname, AssigneeAction action,
        Long changedByMemberId, String changedByNickname, OffsetDateTime changedAt) {
    static AssigneeHistoryResponse from(TaskAssigneeHistory history) {
        return new AssigneeHistoryResponse(history.getId(), history.getMember().getId(),
                history.getMember().getUser().getNickname(), history.getAction(), history.getChangedBy().getId(),
                history.getChangedBy().getUser().getNickname(), history.getChangedAt());
    }
}
