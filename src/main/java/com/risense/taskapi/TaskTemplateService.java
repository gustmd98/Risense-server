package com.risense.taskapi;

import com.risense.api.error.ApiException;
import com.risense.domain.task.Task;
import com.risense.domain.task.TaskRepository;
import com.risense.project.ProjectAccess;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskTemplateService {
    private final ProjectAccess access;
    private final TaskRepository tasks;
    private final Clock clock;

    public TaskTemplateService(ProjectAccess access, TaskRepository tasks, Clock clock) {
        this.access = access; this.tasks = tasks; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TaskTemplate.Summary> list(long projectId, long userId) {
        access.project(projectId);
        access.approvedMember(projectId, userId);
        return Arrays.stream(TaskTemplate.values()).map(TaskTemplate::summary).toList();
    }

    @Transactional
    public List<TaskResponse> create(long projectId, long userId, TaskTemplateRequest request) {
        var project = access.lockedProject(projectId);
        access.manager(projectId, userId);
        access.requireWritable(project);
        var items = request.template().items();
        int max = tasks.maxSortOrder(projectId);
        if ((long) max + items.size() > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.CONFLICT, "TASK_ORDER_LIMIT", "작업 정렬 순서 범위를 초과했습니다.");
        }
        var now = OffsetDateTime.now(clock);
        var created = new ArrayList<Task>();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            created.add(Task.create(project, item.title(), item.size(), request.dueDate(), max + i + 1, now));
        }
        return tasks.saveAllAndFlush(created).stream().map(task -> TaskResponse.from(task, List.of())).toList();
    }
}
