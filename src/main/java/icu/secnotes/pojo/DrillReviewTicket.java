package icu.secnotes.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrillReviewTicket {
    private Integer id;
    private Integer attemptId;
    private Integer taskId;
    private Integer checkpointId;
    private Integer userId;
    private Integer reviewerId;
    private Integer originalPassed;
    private Integer originalScore;
    private String reviewStatus;
    private Integer overriddenPassed;
    private Integer overriddenScore;
    private String reason;
    private String reviewComment;
    private LocalDateTime createTime;
    private LocalDateTime reviewTime;
}
