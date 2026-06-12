package icu.secnotes.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRecord {
    private Integer id;
    private Integer evidenceId;
    private Integer instanceId;
    private Integer taskId;
    private Integer checkpointId;
    private Integer reviewerId;
    private String originalJudgment;  // HIT or MISS
    private String newJudgment;       // HIT or MISS
    private String reason;
    private Integer scoreAdjustment;
    private LocalDateTime createTime;
}
