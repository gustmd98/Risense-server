package com.risense.domain.task;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TaskAssigneeId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;
}
