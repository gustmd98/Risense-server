package com.risense.taskapi;

import com.risense.domain.task.Task;
import com.risense.domain.task.TaskStatus;
import java.time.OffsetDateTime;

/** Shared progress calculation for subtask changes. Keeps the existing integer API contract. */
public final class TaskProgress {
    private TaskProgress() {}

    public static void apply(Task task, int total, long completed, OffsetDateTime now) {
        if (total < 0 || completed < 0 || completed > total) throw new IllegalArgumentException("Invalid subtask counts");
        if (task.getStatus() == TaskStatus.CANCELLED) return;
        int progress = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
        var status = task.getStatus();
        if (total > 0 && completed == total) status = TaskStatus.DONE;
        else if (completed > 0 || status == TaskStatus.DONE) status = TaskStatus.IN_PROGRESS;
        boolean changed = !java.util.Objects.equals(task.getProgress(), progress) || task.getStatus() != status;
        if (status == TaskStatus.DONE && task.getStatus() != TaskStatus.DONE) task.setCompletedAt(now);
        else if (status != TaskStatus.DONE && task.getCompletedAt() != null) { task.setCompletedAt(null); changed = true; }
        task.setProgress(progress);
        task.setStatus(status);
        if (changed) task.setUpdatedAt(now);
    }
}
