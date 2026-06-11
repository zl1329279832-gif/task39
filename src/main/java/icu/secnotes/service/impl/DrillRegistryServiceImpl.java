package icu.secnotes.service.impl;

import icu.secnotes.service.DrillRegistryService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DrillRegistryServiceImpl implements DrillRegistryService {

    private static final Map<String, Map<String, Object>> REGISTRY;

    static {
        Map<String, Map<String, Object>> reg = new LinkedHashMap<>();

        reg.put("SQLI_NUMERIC", buildEntry("/sqli/jdbc/getUserById", "/sqli/jdbc/getUserSecById",
                "GET", "id", "username.*password", null));

        reg.put("SQLI_STRING", buildEntry("/sqli/jdbc/getUserByUsername", "/sqli/jdbc/getUserSecByUsername",
                "GET", "username", "username.*password", null));

        reg.put("SQLI_ERROR", buildEntry("/sqli/error/getUserByUsernameError", "/sqli/error/getUserSecByUsername",
                "GET", "username", "updatexml|extractvalue", null));

        reg.put("SQLI_BOOLEAN_BLIND", buildEntry("/sqli/boolean/jdbc/checkUserExistsVuln", "/sqli/boolean/jdbc/checkUserExistsSec",
                "GET", "username", "true|exists", null));

        reg.put("SQLI_TIME_BLIND", buildEntry("/sqli/time/getUserByUsernameTime", "/sqli/time/getUserByUsernameTimeSafe",
                "GET", "username", "sleep|benchmark", null));

        reg.put("SQLI_UNION", buildEntry("/sqli/union/getArticleVuln", "/sqli/union/getArticleSec",
                "GET", "id", "UNION|union", null));

        reg.put("SQLI_ORDERBY", buildEntry("/sqli/orderby/vuln", "/sqli/orderby/sec1",
                "GET", "orderBy", "order.*by", null));

        reg.put("XXE_BASIC", buildEntry("/xml/xxe/vuln", "/xml/xxe/sec",
                "POST", "xml", "root:.*:0:0", null));

        reg.put("XXE_SSRF", buildEntry("/xml/xxe-ssrf/vuln", "/xml/xxe-ssrf/sec",
                "POST", "xml", "root:.*:0:0|ENTITY", null));

        reg.put("SSRF", buildEntry("/ssrf/vuln1", "/ssrf/sec1",
                "GET", "url", "internal|localhost", null));

        reg.put("PATH_TRAVERSAL", buildEntry("/pathtraversal/vuln1", "/pathtraversal/sec1",
                "GET", "filename", "root:.*:0:0|\\[boot\\]", "path.*validation|canonical"));

        reg.put("ZIP_SLIP", buildEntry("/zipslip/vuln", "/zipslip/sec",
                "POST", "file", "extracted|uploaded", "canonical.*path"));

        reg.put("DESERIALIZE", buildEntry("/deserialize/base64Deserialize", "/deserialize/secureDeserialize",
                "POST", "data", "uid=|root", null));

        reg.put("JWT_SIGNATURE", buildEntry("/jwt/signature/vulnLogin", "/jwt/signature/secureLogin",
                "POST", "username,password", "token|jwt", null));

        reg.put("JWT_WEAK_KEY", buildEntry("/jwt/weak/weakLogin", "/jwt/weak/strongLogin",
                "POST", "username,password", "token|jwt", null));

        reg.put("HORIZONTAL_PRIV", buildEntry("/accessControl/HorizontalPri/vuln1/1", "/accessControl/HorizontalPri/sec1/1",
                "GET", "userId", "secret|mfa", null));

        reg.put("UNAUTHORIZED_PRIV", buildEntry("/accessControl/UnauthorizedPri/vuln1/1", "/accessControl/UnauthorizedPri/sec1/1",
                "GET", "userId", "secret|mfa", null));

        reg.put("MFA_BYPASS", buildEntry("/authentication/mfaBased/changePasswordVuln", "/authentication/mfaBased/changePasswordSec",
                "POST", "userId,newPassword", "success|密码修改", null));

        reg.put("BRUTE_FORCE", buildEntry("/authentication/passwordBased/vuln1", "/authentication/passwordBased/sec",
                "POST", "username,password", "token|login", null));

        reg.put("XSS_REFLECTED", buildEntry("/xss/reflected/vuln1", "/xss/reflected/sec1",
                "GET", "content", "<script>|alert", null));

        reg.put("XSS_STORED", buildEntry("/xss/stored/addMessage", "/xss/stored/addMessageSec",
                "POST", "message", "script|onerror", null));

        reg.put("RCE", buildEntry("/rce/vulnPing", "/rce/secPing",
                "GET", "ip", "uid=|root|Windows", null));

        reg.put("CSRF", buildEntry("/csrf/changePasswordVuln", "/csrf/changePasswordSecure",
                "POST", "newPassword", "success|密码修改", null));

        reg.put("SSTI", buildEntry("/ssti/vuln", "/ssti/sec/whitelist",
                "GET", "lang", "Runtime|exec|uid=", null));

        reg.put("SPEL", buildEntry("/spel/vuln", "/spel/sec",
                "POST", "expression", "Runtime|exec|uid=", null));

        REGISTRY = Collections.unmodifiableMap(reg);
    }

    private static Map<String, Object> buildEntry(String vulnEndpoint, String secEndpoint,
                                                   String httpMethod, String targetParam,
                                                   String verifyPattern, String defensePattern) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("vulnEndpoint", vulnEndpoint);
        entry.put("secEndpoint", secEndpoint);
        entry.put("httpMethod", httpMethod);
        entry.put("targetParam", targetParam);
        entry.put("verifyPattern", verifyPattern);
        if (defensePattern != null) {
            entry.put("defensePattern", defensePattern);
        }
        return entry;
    }

    @Override
    public Map<String, Map<String, Object>> getRegistry() {
        return REGISTRY;
    }
}
