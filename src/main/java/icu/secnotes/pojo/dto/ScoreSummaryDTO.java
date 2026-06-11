package icu.secnotes.pojo.dto;

import lombok.Data;
import java.util.List;

@Data
public class ScoreSummaryDTO {
    private Integer taskId;
    private String taskTitle;
    private Integer userId;
    private String username;
    private Integer totalScore;
    private Integer maxPossible;
    private Integer checkpointsPassed;
    private Integer checkpointsTotal;
    private Double completionPct;
    private List<CheckpointScore> checkpointScores;

    @Data
    public static class CheckpointScore {
        private Integer checkpointId;
        private String vulnCategory;
        private String mode;
        private Integer score;
        private Integer maxScore;
        private Boolean passed;
        private Integer hintsUsed;
        private String deductionItems;
    }
}
