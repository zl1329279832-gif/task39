package icu.secnotes.pojo.dto;

import lombok.Data;
import java.util.List;

@Data
public class DrillTaskCreateRequest {
    private String title;
    private String description;
    private String difficulty;
    private List<CheckpointDef> checkpoints;

    @Data
    public static class CheckpointDef {
        private String vulnCategory;
        private Integer checkpointOrder;
        private String mode;
        private Integer maxScore;
        private Integer maxHints;
        private Integer timeLimit;
        private Integer prerequisiteOrder;
        private String customVerifyPattern;
        private String customDefensePattern;
        private String hintContent;
    }
}
