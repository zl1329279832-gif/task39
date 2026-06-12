package icu.secnotes.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrillAuditLog {
    private Integer id;
    private Integer actorId;
    private String action;
    private String targetType;
    private Integer targetId;
    private String details;
    private String ipAddress;
    private LocalDateTime createTime;
}
