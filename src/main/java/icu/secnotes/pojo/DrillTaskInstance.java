package icu.secnotes.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrillTaskInstance {
    private Integer id;
    private Integer taskId;
    private Integer userId;
    private String status;          // IN_PROGRESS, COMPLETED, EXPIRED
    private String rulesSnapshot;   // JSON: checkpoint configs at start time
    private LocalDateTime startTime;
    private LocalDateTime deadline;
    private Integer totalScore;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
