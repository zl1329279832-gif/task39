package icu.secnotes.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrillReviewRule {
    private Integer id;
    private Integer taskId;
    private Integer autoApproveThreshold;
    private Integer manualReviewBelow;
    private String manualReviewTriggers;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
