package com.risense.checkin;

import com.risense.project.ProjectAccess;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.checkin.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class CheckinScheduler {
    private static final Logger log=LoggerFactory.getLogger(CheckinScheduler.class);
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ProjectAccess access;
    private final CheckinLifecycle lifecycle;
    public CheckinScheduler(JdbcTemplate db,TransactionTemplate tx,ProjectAccess access,CheckinLifecycle lifecycle) {
        this.db=db;this.tx=tx;this.access=access;this.lifecycle=lifecycle;
    }
    @Scheduled(fixedDelayString="${app.checkin.scan-delay-ms:60000}",initialDelayString="${app.checkin.scan-delay-ms:60000}")
    public void generate() {
        long cursor=0;
        while(true) {
            var ids=db.queryForList("SELECT id FROM projects WHERE status='IN_PROGRESS' AND id>? ORDER BY id LIMIT 100",Long.class,cursor);
            if(ids.isEmpty()) return;
            for(long id:ids) {
                try { tx.executeWithoutResult(s->{ access.lockedProject(id); lifecycle.beforeChange(id); }); }
                catch(RuntimeException e) { log.error("Check-in generation failed for project {}",id,e); }
            }
            cursor=ids.getLast();
        }
    }
}
