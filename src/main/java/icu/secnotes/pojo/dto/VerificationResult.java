package icu.secnotes.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerificationResult {
    private boolean passed;
    private String message;

    public static VerificationResult pass(String msg) {
        return new VerificationResult(true, msg);
    }

    public static VerificationResult fail(String msg) {
        return new VerificationResult(false, msg);
    }
}
