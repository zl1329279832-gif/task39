package icu.secnotes.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrillTask {
    private Integer id;
    private String title;
    private String description;
    private String difficulty;
    private Integer creatorId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
