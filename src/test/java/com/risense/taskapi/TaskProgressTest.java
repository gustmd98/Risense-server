package com.risense.taskapi;

import static org.assertj.core.api.Assertions.*;
import com.risense.domain.task.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TaskProgressTest {
    private final OffsetDateTime now = OffsetDateTime.parse("2026-10-10T00:00:00Z");
    private Task task() { return Task.create(null, "task", TaskSize.S, null, 0, now.minusDays(1)); }

    @Test void completesOnlyNonemptyFullyCompletedTask() {
        var task = task();
        TaskProgress.apply(task, 0, 0, now);
        assertThat(task.getProgress()).isZero();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        TaskProgress.apply(task, 3, 3, now);
        assertThat(task.getProgress()).isEqualTo(100);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(task.getCompletedAt()).isEqualTo(now);
    }
    @Test void addingIncompleteItemReopensCompletedTaskAndRoundsIntegerResponse() {
        var task = task(); task.setStatus(TaskStatus.DONE); task.setCompletedAt(now.minusHours(1));
        TaskProgress.apply(task, 3, 2, now);
        assertThat(task.getProgress()).isEqualTo(67);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getCompletedAt()).isNull();
    }
    @Test void deletingLastItemClearsCompletion() {
        var task = task(); task.setStatus(TaskStatus.DONE); task.setCompletedAt(now);
        TaskProgress.apply(task, 0, 0, now);
        assertThat(task.getProgress()).isZero();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getCompletedAt()).isNull();
    }
    @Test void cancellationNeverResumesAutomatically() {
        var task = task(); task.cancel(now.minusHours(1));
        TaskProgress.apply(task, 1, 1, now);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(task.getCancelledAt()).isEqualTo(now.minusHours(1));
    }
    @Test void noOpDoesNotChangeCompletionOrUpdateTimestamp() {
        var task = task(); TaskProgress.apply(task, 1, 1, now);
        TaskProgress.apply(task, 1, 1, now.plusHours(1));
        assertThat(task.getCompletedAt()).isEqualTo(now);
        assertThat(task.getUpdatedAt()).isEqualTo(now);
    }
    @Test void roundedHundredDoesNotCompleteUnfinishedTask() {
        var task = task();
        TaskProgress.apply(task, 200, 199, now);
        assertThat(task.getProgress()).isEqualTo(100);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getCompletedAt()).isNull();
    }

}
