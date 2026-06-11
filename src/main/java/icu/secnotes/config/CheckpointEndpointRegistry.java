package icu.secnotes.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * 检查点端点注册表
 * 将漏洞类别映射到对应的 vuln/sec 端点对和默认验证模式
 * 这是演练系统的单一真实来源，端点路径来源于实际 Controller 代码
 */
@Component
public class CheckpointEndpointRegistry {

    private static final Map<String, EndpointPair> REGISTRY = new LinkedHashMap<>();

    static {
        // SQL 注入
        REGISTRY.put("SQLI_NUMERIC", new EndpointPair(
                "/sqli/jdbc/getUserById", "/sqli/jdbc/getUserSecById",
                "GET", "id", "\"username\"", "error|查询失败"));
        REGISTRY.put("SQLI_STRING", new EndpointPair(
                "/sqli/jdbc/getUserByUsername", "/sqli/jdbc/getUserSecByUsername",
                "GET", "username", "\"password\"", "error|检测"));
        REGISTRY.put("SQLI_UNION", new EndpointPair(
                "/sqli/union/getArticleVuln", "/sqli/union/getArticleSec",
                "GET", "id", "admin|information_schema", "查询失败|稍后重试"));
        REGISTRY.put("SQLI_ERROR", new EndpointPair(
                "/sqli/jdbc/getUserByIdError", "/sqli/jdbc/getUserSecById",
                "GET", "id", "SQLException|syntax|error", "error|查询失败"));
        REGISTRY.put("SQLI_ORDERBY", new EndpointPair(
                "/sqli/orderby/vuln", "/sqli/orderby/sec1",
                "GET", "orderBy", "password|username", "error|不支持"));

        // XXE / XML 安全
        REGISTRY.put("XXE_BASIC", new EndpointPair(
                "/xml/xxe/vuln", "/xml/xxe/sec",
                "POST", null, "root:|/etc/|daemon", "安全机制生效|XML解析失败"));
        REGISTRY.put("XXE_SAX", new EndpointPair(
                "/xml/xxe/sax/vuln", "/xml/xxe/sax/sec",
                "POST", null, "root:|/etc/", "安全机制生效"));
        REGISTRY.put("XXE_DOM4J", new EndpointPair(
                "/xml/xxe/dom4j/vuln", "/xml/xxe/dom4j/sec",
                "POST", null, "root:|/etc/", "安全机制生效"));
        REGISTRY.put("XXE_STAX", new EndpointPair(
                "/xml/xxe/stax/vuln", "/xml/xxe/stax/sec",
                "POST", null, "root:|/etc/", "安全机制生效"));

        // SSRF
        REGISTRY.put("SSRF_BASIC", new EndpointPair(
                "/ssrf/vuln1", "/ssrf/sec1",
                "GET", "url", "image|png|jpg|gif|svg|data", "不允许访问内网|只支持|不支持"));

        // JWT 安全
        REGISTRY.put("JWT_WEAK", new EndpointPair(
                "/jwt/weak/weakGetInfo", "/jwt/weak/strongGetInfo",
                "GET", null, "\"username\"", "JWT解析失败|签名验证失败"));
        REGISTRY.put("JWT_SIGNATURE", new EndpointPair(
                "/jwt/signature/vulnGetInfo", "/jwt/signature/secureGetInfo",
                "GET", null, "\"username\"", "JWT解析失败|签名"));
        REGISTRY.put("JWT_CONFUSION", new EndpointPair(
                "/jwt/algorithmConfusion/verifyVulnerable", "/jwt/algorithmConfusion/verifySecure",
                "GET", null, "\"username\"", "JWT解析失败|签名验证失败"));

        // 反序列化
        REGISTRY.put("DESERIALIZE", new EndpointPair(
                "/deserialize/base64Deserialize", "/deserialize/secureDeserialize",
                "POST", null, "反序列化成功|Person", "不允许反序列化类|class is not allowed"));

        // 路径穿越
        REGISTRY.put("PATH_TRAVERSAL", new EndpointPair(
                "/pathtraversal/vuln1", "/pathtraversal/sec1",
                "GET", "filename", "root:|/etc/passwd|password", "文件名不合法|Access denied"));

        // 权限控制
        REGISTRY.put("ACCESS_CONTROL_H", new EndpointPair(
                "/accessControl/HorizontalPri/vuln1/1", "/accessControl/HorizontalPri/sec1/1",
                "GET", null, "[a-zA-Z0-9]{16,}|secret", "无权访问|身份验证失败"));
        REGISTRY.put("ACCESS_CONTROL_U", new EndpointPair(
                "/accessControl/UnauthorizedPri/vuln1/1", "/accessControl/UnauthorizedPri/sec1/1",
                "GET", null, "[a-zA-Z0-9]{16,}|secret", "无权访问|身份验证失败|未授权"));

        // MFA 绕过
        REGISTRY.put("MFA_BYPASS", new EndpointPair(
                "/authentication/mfaBased/changePasswordVuln", "/authentication/mfaBased/changePasswordSec",
                "POST", null, "密码修改成功", "MFA验证码错误|未绑定MFA|mfaCode"));
    }

    public EndpointPair getEndpoints(String vulnCategory) {
        return REGISTRY.get(vulnCategory);
    }

    public Map<String, EndpointPair> getAllEndpoints() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    @Data
    @AllArgsConstructor
    public static class EndpointPair {
        private String vulnEndpoint;
        private String secEndpoint;
        private String httpMethod;
        private String targetParam;
        private String defaultExploitPattern;
        private String defaultDefensePattern;
    }
}
