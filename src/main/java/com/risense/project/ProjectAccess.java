package com.risense.project;

import com.risense.api.error.ApiException;
import com.risense.domain.member.*;
import com.risense.domain.project.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Call inside the caller's transaction. All future project writes must use the same project lock. */
@Component
public class ProjectAccess {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;

    public ProjectAccess(ProjectRepository projects, ProjectMemberRepository members) {
        this.projects = projects;
        this.members = members;
    }

    public Project project(long projectId) {
        return projects.findById(projectId).orElseThrow(ProjectAccess::notFound);
    }

    public Project lockedProject(long projectId) {
        return projects.findLockedById(projectId).orElseThrow(ProjectAccess::notFound);
    }

    public ProjectMember approvedMember(long projectId, long userId) {
        return members.findByProject_IdAndUser_Id(projectId, userId)
                .filter(m -> m.getJoinStatus() == MemberStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED", "승인된 프로젝트 멤버만 접근할 수 있습니다."));
    }

    public ProjectMember manager(long projectId, long userId) {
        ProjectMember member = approvedMember(projectId, userId);
        if (member.getRole() != MemberRole.LEADER && member.getRole() != MemberRole.CO_LEADER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROJECT_MANAGER_REQUIRED", "팀장 또는 공동 팀장 권한이 필요합니다.");
        }
        return member;
    }

    public void requireWritable(Project project) {
        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE", "완료 또는 종료된 프로젝트는 수정할 수 없습니다.");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다.");
    }
}
