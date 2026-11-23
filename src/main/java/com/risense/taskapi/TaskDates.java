package com.risense.taskapi;

import com.risense.api.error.ApiException;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

final class TaskDates {
    private TaskDates() {}
    static void validate(LocalDate start, LocalDate due) {
        if (start != null && due != null && start.isAfter(due))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_DATES", "시작일은 마감일보다 늦을 수 없습니다.");
    }
}
