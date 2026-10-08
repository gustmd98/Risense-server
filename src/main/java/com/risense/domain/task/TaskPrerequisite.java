package com.risense.domain.task;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "task_prerequisites")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskPrerequisite {

    public static TaskPrerequisite of(Task task, Task prerequisite) {
        TaskPrerequisite relation = new TaskPrerequisite();
        relation.task = task;
        relation.prerequisiteTask = prerequisite;
        relation.id = new TaskPrerequisiteId(task.getId(), prerequisite.getId());
        return relation;
    }

    @EmbeddedId
    private TaskPrerequisiteId id;

    @MapsId("taskId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, foreignKey = @ForeignKey(name = "fk_prereq_task"))
    private Task task;

    @MapsId("prerequisiteTaskId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prerequisite_task_id", nullable = false, foreignKey = @ForeignKey(name = "fk_prereq_prereq"))
    private Task prerequisiteTask;
}
