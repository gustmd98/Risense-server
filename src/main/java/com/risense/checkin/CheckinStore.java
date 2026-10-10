package com.risense.checkin;

import static com.risense.checkin.CheckinData.*;
import jakarta.persistence.EntityManager;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** B-only SQL. Flush JPA before reading A state; both share the caller's transaction. */
@Repository
public class CheckinStore {
    private final JdbcTemplate db;
    private final EntityManager em;
    private final ObjectMapper json;
    public CheckinStore(JdbcTemplate db, EntityManager em, ObjectMapper json) {
        this.db=db; this.em=em; this.json=json;
    }
    public JdbcTemplate jdbc() { return db; }
    void flush() { em.flush(); }
    String encode(Object value) { return json.writeValueAsString(value); }
    Submission decodeSubmission(String value) { return json.readValue(value, Submission.class); }
    List<TaskSnapshot> decodeTasks(String value) {
        return Arrays.asList(json.readValue(value, TaskSnapshot[].class));
    }
    static Instant instant(ResultSet rs, String name) throws SQLException {
        var value=rs.getObject(name, OffsetDateTime.class); return value==null?null:value.toInstant();
    }
    static final RowMapper<Round> ROUND=(rs,n)->new Round(rs.getLong("id"),rs.getLong("project_id"),
            rs.getObject("scheduled_date",LocalDate.class),instant(rs,"opens_at"),instant(rs,"deadline_at"),instant(rs,"late_until_at"));
    Optional<Round> round(long projectId,long roundId) {
        return db.query("SELECT * FROM checkin_rounds WHERE project_id=? AND id=?",ROUND,projectId,roundId).stream().findFirst();
    }
    Optional<Round> latest(long projectId) {
        return db.query("SELECT * FROM checkin_rounds WHERE project_id=? ORDER BY scheduled_date DESC LIMIT 1",ROUND,projectId).stream().findFirst();
    }
    Submission submission(long roundId,long memberId) {
        return db.query("SELECT * FROM checkin_submissions WHERE round_id=? AND member_id=?",(rs,n)->
            new Submission(roundId,rs.getLong("revision"),rs.getBoolean("is_late"),instant(rs,"submitted_at"),
                    instant(rs,"updated_at"),decodeTasks(rs.getString("snapshot"))),roundId,memberId).stream().findFirst().orElse(null);
    }
    List<Target> targets(long roundId,long memberId) {
        return db.query("SELECT * FROM checkin_targets WHERE round_id=? AND member_id=? ORDER BY task_id",(rs,n)-> {
            long id=rs.getLong("task_id"); var current=snapshot(id,null,null);
            return new Target(id,rs.getString("title_at_open"),rs.getString("excluded_reason"),hash(encode(current)),current);
        },roundId,memberId);
    }
    TaskSnapshot snapshot(long taskId,String requestToTeam,String nextAction) {
        var assignees=db.queryForList("SELECT member_id FROM task_assignees WHERE task_id=? ORDER BY member_id",Long.class,taskId);
        var children=db.query("SELECT * FROM sub_tasks WHERE task_id=? ORDER BY id",(rs,n)->new Child(rs.getLong("id"),
                rs.getString("title"),rs.getBoolean("completed"),(Long)rs.getObject("assignee_member_id")),taskId);
        var artifacts=db.query("SELECT * FROM task_artifacts WHERE task_id=? ORDER BY id",(rs,n)->new Artifact(rs.getLong("id"),
                rs.getString("title"),rs.getString("url"),rs.getLong("created_by")),taskId);
        var issue=db.query("SELECT * FROM task_issues WHERE task_id=?",(rs,n)->new Issue(rs.getBoolean("open"),rs.getString("content"),
                rs.getLong("revision"),(Long)rs.getObject("reported_by"),instant(rs,"reported_at"),
                (Long)rs.getObject("resolved_by"),instant(rs,"resolved_at")),taskId).stream().findFirst()
                .orElse(new Issue(false,null,0,null,null,null,null));
        return db.queryForObject("SELECT * FROM tasks WHERE id=?",(rs,n)->new TaskSnapshot(taskId,rs.getString("title"),
                rs.getString("size"),rs.getString("status"),rs.getInt("progress"),rs.getObject("due_date",LocalDate.class),
                instant(rs,"updated_at"),assignees,children.size(),
                children.stream().filter(Child::completed).count(),children,artifacts,issue,requestToTeam,nextAction),taskId);
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
