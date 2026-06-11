package icu.secnotes.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrillAttempt {
    private Integer id;
    private Integer taskId;
    private Integer checkpointId;
    private Integer userId;
    private LocalDateTime attemptTime;
    private String payloadSummary;
    private String evidence;
    private Integer elapsedSeconds;
    private Integer hintsUsed;
    private String deductionItems;
    private Integer score;
    private Integer passed;
    private LocalDateTime createTime;
}
