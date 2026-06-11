package icu.secnotes.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrillScoreSummary {
    private Integer id;
    private Integer taskId;
    private Integer userId;
    private Integer totalScore;
    private Integer maxPossible;
    private Integer checkpointsPassed;
    private Integer checkpointsTotal;
    private BigDecimal completionPct;
    private LocalDateTime lastAttemptTime;
    private LocalDateTime updateTime;
}
