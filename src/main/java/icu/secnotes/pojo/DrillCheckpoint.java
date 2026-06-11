package icu.secnotes.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrillCheckpoint {
    private Integer id;
    private Integer taskId;
    private String vulnCategory;
    private Integer checkpointOrder;
    private String mode;
    private String vulnEndpoint;
    private String secEndpoint;
    private String httpMethod;
    private String targetParam;
    private Integer maxScore;
    private Integer maxHints;
    private Integer timeLimit;
    private Integer prerequisiteId;
    private String verifyPattern;
    private String defensePattern;
    private String hintContent;
    private LocalDateTime createTime;
}
