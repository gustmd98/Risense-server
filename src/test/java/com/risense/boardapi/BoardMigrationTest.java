package com.risense.boardapi;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="BOARD_TEST_DB",matches="disposable")
class BoardMigrationTest {
    @Test void upgradeFromV4PreservesExistingRowsAndBackfillsOnlySurvivingIssueEvents() throws Exception {
        String url=System.getenv("DB_URL"),user=System.getenv("DB_USERNAME"),password=System.getenv("DB_PASSWORD");
        String schema="board_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var old=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("4").load();
        try {
            old.migrate();
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) {
                c.setSchema(schema);
                s.execute("INSERT INTO users(email,password_hash,nickname,created_at) VALUES('upgrade@example.com','hash','upgrade',now())");
                s.execute("INSERT INTO projects(title,deadline,checkin_time,checkin_frequency,status,created_by,created_at,updated_at) VALUES('old','2026-12-20','23:00',1,'IN_PROGRESS',1,now(),now())");
                s.execute("INSERT INTO project_members(project_id,user_id,role,join_status,requested_at,joined_at) VALUES(1,1,'LEADER','APPROVED',now(),now())");
                s.execute("INSERT INTO tasks(project_id,title,size,status,progress,sort_order,created_at,updated_at) VALUES(1,'old task','M','TODO',0,0,now(),now())");
                s.execute("INSERT INTO task_issues(task_id,open,content,revision,reported_by,resolved_by,reported_at,resolved_at) VALUES(1,false,'old issue',2,1,1,now()-interval '1 day',now())");
            }
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate();
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) {
                c.setSchema(schema);
                try(var rs=s.executeQuery("SELECT start_date,revision FROM tasks WHERE id=1")) {
                    assertThat(rs.next()).isTrue();assertThat(rs.getObject(1)).isNull();assertThat(rs.getLong(2)).isZero();
                }
                try(var rs=s.executeQuery("SELECT project_type FROM projects WHERE id=1")){rs.next();assertThat(rs.getString(1)).isEqualTo("other");}
                try(var rs=s.executeQuery("SELECT count(*) FROM task_issue_events WHERE backfilled")){rs.next();assertThat(rs.getLong(1)).isEqualTo(2);}
                s.execute("UPDATE task_issues SET open=true,content='new issue',revision=3,reported_at=now(),resolved_at=null,resolved_by=null WHERE task_id=1");
                try(var rs=s.executeQuery("SELECT count(*) FROM task_issue_events WHERE NOT backfilled")){rs.next();assertThat(rs.getLong(1)).isEqualTo(1);}
            }
        } finally {
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).cleanDisabled(false).load().clean();
        }
    }
}
