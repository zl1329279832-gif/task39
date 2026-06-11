package icu.secnotes.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrillTaskCreateRequest {
    private String title;
    private String description;
    private String difficulty;
    private List<CheckpointDef> checkpoints;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckpointDef {
        private String vulnCategory;
        private Integer checkpointOrder;
        private String mode;
        private Integer maxScore;
        private Integer maxHints;
        private Integer timeLimit;
        private String vulnEndpoint;
        private String secEndpoint;
        private String httpMethod;
        private String targetParam;
        private String verifyPattern;
        private String defensePattern;
        private String hintContent;
        private Integer prerequisiteId;
    }
}
