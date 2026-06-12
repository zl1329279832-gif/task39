package icu.secnotes.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    private Integer id;
    private String action;       // TASK_CREATE, EVIDENCE_SUBMIT, REVIEW, SCORE_ADJUST, etc.
    private Integer actorId;
    private String actorRole;    // admin, guest
    private String targetType;   // TASK, INSTANCE, EVIDENCE, REVIEW, CHECKPOINT
    private Integer targetId;
    private String detail;       // JSON detail
    private String ipAddress;
    private LocalDateTime createTime;
}
