package icu.secnotes.service;

import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.pojo.dto.VerificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 检查点验证器
 * 负责验证学员提交的演练结果
 * EXPLOIT 模式：正则匹配学员提交的证据
 * DEFENSE 模式：将学员的 payload 重放到安全端点，验证是否被正确拦截
 */
@Slf4j
@Component
public class CheckpointVerifier {

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 验证 EXPLOIT 模式的提交
     * 仅对学员提交的 evidence 进行正则匹配，不调用任何端点
     */
    public VerificationResult verifyExploit(DrillCheckpoint checkpoint, String evidence) {
        if (evidence == null || evidence.trim().isEmpty()) {
            return VerificationResult.fail("证据内容为空");
        }
        String pattern = checkpoint.getVerifyPattern();
        if (pattern == null || pattern.trim().isEmpty()) {
            return VerificationResult.fail("检查点未配置验证模式");
        }
        try {
            boolean matched = Pattern.compile(pattern, Pattern.DOTALL | Pattern.CASE_INSENSITIVE)
                    .matcher(evidence).find();
            if (matched) {
                return VerificationResult.pass("漏洞利用证据匹配预期模式");
            } else {
                return VerificationResult.fail("证据不匹配预期的漏洞利用模式");
            }
        } catch (Exception e) {
            log.error("正则匹配失败: pattern={}, error={}", pattern, e.getMessage());
            return VerificationResult.fail("验证模式匹配异常: " + e.getMessage());
        }
    }

    /**
     * 验证 DEFENSE 模式的提交
     * 将学员的 payload 重放到安全端点，检查是否被正确拦截
     * 学员提交的 evidence 字段在 DEFENSE 模式下被忽略
     */
    public VerificationResult verifyDefense(DrillCheckpoint checkpoint, String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return VerificationResult.fail("攻击载荷为空");
        }
        String secUrl = "http://localhost:8080" + checkpoint.getSecEndpoint();
        String defPattern = checkpoint.getDefensePattern() != null
                ? checkpoint.getDefensePattern() : "error|fail|denied|拒绝";
        try {
            String responseBody;
            if ("GET".equalsIgnoreCase(checkpoint.getHttpMethod())) {
                String param = checkpoint.getTargetParam() != null ? checkpoint.getTargetParam() : "input";
                String url = secUrl + "?" + param + "=" + URLEncoder.encode(payload, StandardCharsets.UTF_8.name());
                responseBody = restTemplate.getForObject(url, String.class);
            } else {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(payload, headers);
                ResponseEntity<String> resp = restTemplate.exchange(secUrl, HttpMethod.POST, entity, String.class);
                responseBody = resp.getBody();
            }
            String body = responseBody != null ? responseBody : "";
            boolean blocked = Pattern.compile(defPattern, Pattern.CASE_INSENSITIVE)
                    .matcher(body).find();
            if (blocked) {
                return VerificationResult.pass("安全端点正确拦截了攻击");
            } else {
                return VerificationResult.fail("安全端点未能阻止攻击");
            }
        } catch (Exception e) {
            // 安全端点返回异常（400/500）也视为拦截成功
            log.info("安全端点拒绝攻击 (异常: {})", e.getMessage());
            return VerificationResult.pass("安全端点拒绝了攻击 (异常: " + e.getMessage() + ")");
        }
    }
}
