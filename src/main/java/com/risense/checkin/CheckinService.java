package com.risense.checkin;

import static com.risense.checkin.CheckinData.*;
import com.risense.api.error.ApiException;
import com.risense.domain.member.ProjectMember;
import com.risense.domain.project.ProjectStatus;
import com.risense.issue.TaskIssueService;
import com.risense.project.ProjectAccess;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CheckinService {
    private final ProjectAccess access;
    private final CheckinLifecycle lifecycle;
    private final CheckinStore store;
    private final TaskIssueService issues;
    private final Clock clock;
    public CheckinService(ProjectAccess access,CheckinLifecycle lifecycle,CheckinStore store,TaskIssueService issues,Clock clock) {
        this.access=access;this.lifecycle=lifecycle;this.store=store;this.issues=issues;this.clock=clock;
    }
    public Current current(long projectId,long userId) {
        var project=access.lockedProject(projectId);
        var member=access.approvedMember(projectId,userId);
        lifecycle.beforeChange(projectId);
        var round=store.latest(projectId).orElse(null);
        var now=clock.instant();
        if(round==null) return new Current(null,now,null,false,false,"NO_ROUND",List.of(),null);
        var targets=store.targets(round.id(),member.getId());
        var submitted=store.submission(round.id(),member.getId());
        var phase=round.window().phaseAt(now);
        String reason=null;
        if(project.getStatus()!=ProjectStatus.IN_PROGRESS) reason="PROJECT_NOT_WRITABLE";
        else if(!eligible(round.id(),member)) reason="CHECKIN_NOT_ELIGIBLE";
        else if(targets.stream().noneMatch(t->t.excludedReason()==null)) reason="CHECKIN_NO_TARGETS";
        else if(!round.window().allowsSubmission(now,submitted!=null)) reason="CHECKIN_"+phase.name();
        return new Current(round,now,phase,reason==null && submitted==null,reason==null && submitted!=null,reason,targets,submitted);
    }
    private boolean eligible(long roundId,ProjectMember member) {
        return Boolean.TRUE.equals(store.jdbc().queryForObject("""
            SELECT EXISTS(SELECT 1 FROM checkin_participants WHERE round_id=? AND member_id=?
            AND departed_at IS NULL AND excluded_reason IS NULL AND joined_at=?)
            """,Boolean.class,roundId,member.getId(),member.getJoinedAt()));
    }
    public Submission detail(long projectId,long roundId,long userId) {
        access.lockedProject(projectId);var member=access.approvedMember(projectId,userId);
        lifecycle.beforeChange(projectId);round(projectId,roundId);
        var result=store.submission(roundId,member.getId());
        if(result==null) throw error(HttpStatus.NOT_FOUND,"CHECKIN_SUBMISSION_NOT_FOUND","제출 기록이 없습니다.");
        return result;
    }
    public Submission submit(long projectId,long roundId,long userId,Submit request,boolean edit) {
        var project=access.lockedProject(projectId);
        var member=access.approvedMember(projectId,userId);
        access.requireWritable(project);lifecycle.beforeChange(projectId);
        var round=round(projectId,roundId);
        if(!eligible(roundId,member)) throw error(HttpStatus.FORBIDDEN,"CHECKIN_NOT_ELIGIBLE","이 회차에 제출할 권한이 없습니다.");
        var hash=CheckinStore.hash(store.encode(List.of(edit,request)));
        var replay=store.jdbc().query("SELECT payload_hash,response::text FROM checkin_requests WHERE round_id=? AND member_id=? AND request_key=?",
            (rs,n)->Map.entry(rs.getString(1),rs.getString(2)),roundId,member.getId(),request.requestKey());
        if(!replay.isEmpty()) {
            if(!replay.getFirst().getKey().equals(hash)) throw conflict("CHECKIN_REQUEST_KEY_REUSED","다른 요청에 사용한 재요청 키입니다.");
            return store.decodeSubmission(replay.getFirst().getValue());
        }
        var old=store.submission(roundId,member.getId());
        if(edit && old==null) throw conflict("CHECKIN_SUBMISSION_REQUIRED","최초 제출 후 수정할 수 있습니다.");
        if(!edit && old!=null) throw conflict("CHECKIN_ALREADY_SUBMITTED","이미 제출했습니다. 정시 수정 API를 사용해주세요.");
        if(request.expectedRevision()==null || request.expectedRevision()!=(old==null?0:old.revision()))
            throw conflict("CHECKIN_VERSION_CONFLICT","제출 기록이 변경되었습니다. 다시 조회해주세요.");
        var targets=store.targets(roundId,member.getId()).stream().filter(t->t.excludedReason()==null).toList();
        var entries=request.tasks();
        var submittedAt=clock.instant();
        var verdict=CheckinSubmissionPolicy.evaluate(round.window(),submittedAt,old!=null,
            new HashSet<>(targets.stream().map(Target::taskId).toList()),entries==null?null:entries.stream().map(Entry::taskId).toList());
        if(verdict!=CheckinSubmissionPolicy.Result.ALLOWED) throw error(
            verdict==CheckinSubmissionPolicy.Result.INVALID_TASK_IDS?HttpStatus.BAD_REQUEST:HttpStatus.CONFLICT,
            "CHECKIN_"+verdict.name(),"제출 시간 또는 전체 대상이 변경되었습니다. 다시 조회해주세요.");
        var byId=new HashMap<Long,Target>();targets.forEach(t->byId.put(t.taskId(),t));
        // Validate every shared value before applying any issue report.
        for(var entry:entries) {
            var target=byId.get(entry.taskId());
            if(!Objects.equals(entry.expectedVersion(),target.version()) || entry.expectedIssueRevision()!=target.current().issue().revision())
                throw conflict("CHECKIN_TASK_CHANGED","작업 또는 이슈가 변경되었습니다. 다시 조회해주세요.");
            if(entry.issueContent()!=null && (entry.issueContent().isBlank() || entry.issueContent().length()>2000))
                throw error(HttpStatus.BAD_REQUEST,"INVALID_ISSUE_CONTENT","이슈 내용을 1~2000자로 입력해주세요.");
        }
        for(var entry:entries) if(entry.issueContent()!=null)
            issues.report(projectId,entry.taskId(),userId,entry.issueContent(),entry.expectedIssueRevision());
        store.flush();
        var snapshots=entries.stream().sorted(Comparator.comparingLong(Entry::taskId))
            .map(e->store.snapshot(e.taskId(),e.requestToTeam(),e.nextAction())).toList();
        var now=submittedAt;
        var result=new Submission(roundId,old==null?1:old.revision()+1,
            old==null?round.window().phaseAt(now)==CheckinWindow.Phase.LATE:old.late(),
            old==null?now:old.submittedAt(),now,snapshots);
        store.jdbc().update("""
            INSERT INTO checkin_submissions(round_id,member_id,revision,is_late,submitted_at,updated_at,snapshot)
            VALUES(?,?,?,?,?,?,CAST(? AS jsonb)) ON CONFLICT(round_id,member_id) DO UPDATE
            SET revision=excluded.revision,updated_at=excluded.updated_at,snapshot=excluded.snapshot
            """,roundId,member.getId(),result.revision(),result.late(),time(result.submittedAt()),time(now),store.encode(snapshots));
        store.jdbc().update("INSERT INTO checkin_requests VALUES(?,?,?,?,CAST(? AS jsonb))",
            roundId,member.getId(),request.requestKey(),hash,store.encode(result));
        return result;
    }
    private Round round(long projectId,long roundId) {
        return store.round(projectId,roundId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"CHECKIN_ROUND_NOT_FOUND","회차를 찾을 수 없습니다."));
    }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private static ApiException conflict(String code,String message) { return error(HttpStatus.CONFLICT,code,message); }
    private static ApiException error(HttpStatus status,String code,String message) { return new ApiException(status,code,message); }
}
