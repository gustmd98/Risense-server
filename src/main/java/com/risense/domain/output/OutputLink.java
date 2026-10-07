package com.risense.domain.output;

import com.risense.domain.checkin.CheckinSubmission;
import com.risense.domain.member.ProjectMember;
import com.risense.domain.task.Task;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 작업당 여러 개 등록 가능. 작성자와 팀장만 삭제 가능. */
@Entity
@Table(name = "output_link")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutputLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private ProjectMember createdBy;

    /** 체크인에서 등록한 경우에만 연결 (nullable) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private CheckinSubmission submission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutputType type;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(length = 300)
    private String title;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
