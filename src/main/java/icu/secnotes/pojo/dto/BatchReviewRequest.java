package icu.secnotes.pojo.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchReviewRequest {
    private Integer taskId;
    private String decision;
    private List<Integer> attemptIds;
    private String reviewComment;
}
