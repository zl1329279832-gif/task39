package icu.secnotes.pojo.dto;

import lombok.Data;

@Data
public class ReviewTicketCreateRequest {
    private Integer attemptId;
    private String reason;
}
