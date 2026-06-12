package icu.secnotes.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreDetail {
    private Integer id;
    private Integer instanceId;
    private Integer taskId;
    private Integer checkpointId;
    private Integer userId;
    private Integer baseScore;
    private Integer hintDeduction;
    private Integer timeDeduction;
    private Integer retryDeduction;
    private Integer reviewAdjustment;
    private Integer finalScore;
    private Integer passed;         // 0 or 1
    private String snapshotJson;    // checkpoint config snapshot at scoring time
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
