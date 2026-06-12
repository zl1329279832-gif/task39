package icu.secnotes.drill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.secnotes.pojo.DrillTask;
import icu.secnotes.pojo.dto.*;
import icu.secnotes.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 审计日志测试
 * 验证管理员操作产生的审计日志记录、查询和权限控制
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrillAuditLogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String guestToken;
    private Integer taskId;
    private Integer checkpointId;
    private Integer attemptId;
    private Integer ticketId;

    @BeforeAll
    void setup() {
        Map<String, Object> adminClaims = new HashMap<>();
        adminClaims.put("id", 1);
        adminClaims.put("username", "admin");
        adminClaims.put("name", "系统管理员");
        adminToken = JwtUtils.generateJwt(adminClaims);

        Map<String, Object> guestClaims = new HashMap<>();
        guestClaims.put("id", 3);
        guestClaims.put("username", "guest");
        guestClaims.put("name", "访客用户");
        guestToken = JwtUtils.generateJwt(guestClaims);
    }

    @Test
    @Order(1)
    void testTaskCreate_CreatesAuditLog() throws Exception {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("审计日志测试任务");
        req.setDescription("验证各操作产生审计日志");
        req.setDifficulty("medium");

        DrillTaskCreateRequest.CheckpointDef cp = new DrillTaskCreateRequest.CheckpointDef();
        cp.setVulnCategory("SQLI_NUMERIC");
        cp.setCheckpointOrder(1);
        cp.setMode("EXPLOIT");
        cp.setMaxScore(100);
        cp.setMaxHints(3);
        cp.setTimeLimit(1800);
        req.setCheckpoints(Collections.singletonList(cp));

        MvcResult result = mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        taskId = json.get("data").get("task").get("id").asInt();
        checkpointId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // 验证审计日志包含 TASK_CREATE
        MvcResult logResult = mockMvc.perform(get("/drill/admin/audit/logs/TASK/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode logs = objectMapper.readTree(logResult.getResponse().getContentAsString()).get("data");
        boolean foundCreate = false;
        for (JsonNode logEntry : logs) {
            if ("TASK_CREATE".equals(logEntry.get("action").asText())) {
                foundCreate = true;
                Assertions.assertEquals(1, logEntry.get("actorId").asInt(), "操作者应为 admin(id=1)");
                break;
            }
        }
        Assertions.assertTrue(foundCreate, "应找到 TASK_CREATE 审计日志");
    }

    @Test
    @Order(2)
    void testTaskUpdate_CreatesAuditLog() throws Exception {
        DrillTask task = new DrillTask();
        task.setTitle("审计日志测试任务-已更新");

        mockMvc.perform(put("/drill/admin/tasks/" + taskId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isOk());

        // 验证审计日志
        MvcResult logResult = mockMvc.perform(get("/drill/admin/audit/logs/TASK/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode logs = objectMapper.readTree(logResult.getResponse().getContentAsString()).get("data");
        boolean foundUpdate = false;
        for (JsonNode logEntry : logs) {
            if ("TASK_UPDATE".equals(logEntry.get("action").asText())) {
                foundUpdate = true;
                break;
            }
        }
        Assertions.assertTrue(foundUpdate, "应找到 TASK_UPDATE 审计日志");
    }

    @Test
    @Order(3)
    void testCheckpointUpdate_CreatesAuditLog() throws Exception {
        icu.secnotes.pojo.DrillCheckpoint cp = new icu.secnotes.pojo.DrillCheckpoint();
        cp.setMaxScore(90);
        cp.setMaxHints(3);
        cp.setTimeLimit(1800);
        cp.setMode("EXPLOIT");
        cp.setVulnCategory("SQLI_NUMERIC");
        cp.setVerifyPattern(".*");
        cp.setDefensePattern(".*");
        cp.setVersion(1);

        mockMvc.perform(put("/drill/admin/checkpoints/" + checkpointId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cp)))
                .andExpect(status().isOk());

        MvcResult logResult = mockMvc.perform(get("/drill/admin/audit/logs/CHECKPOINT/" + checkpointId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode logs = objectMapper.readTree(logResult.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode logEntry : logs) {
            if ("CHECKPOINT_UPDATE".equals(logEntry.get("action").asText())) {
                found = true;
                break;
            }
        }
        Assertions.assertTrue(found, "应找到 CHECKPOINT_UPDATE 审计日志");
    }

    @Test
    @Order(4)
    void testAttemptSubmitAndReset_CreatesAuditLog() throws Exception {
        // guest 提交
        DrillSubmitRequest submitReq = new DrillSubmitRequest();
        submitReq.setTaskId(taskId);
        submitReq.setCheckpointId(checkpointId);
        submitReq.setPayloadSummary("id=1 OR 1=1");
        submitReq.setEvidence("[{\"id\":1,\"username\":\"zhangsan\"},{\"id\":2,\"username\":\"lisi\"}]");
        submitReq.setElapsedSeconds(120);
        submitReq.setHintsUsed(0);

        MvcResult submitResult = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isOk())
                .andReturn();

        // 获取尝试记录ID
        MvcResult attemptsResult = mockMvc.perform(get("/drill/student/attempts/" + taskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode attempts = objectMapper.readTree(attemptsResult.getResponse().getContentAsString()).get("data");
        attemptId = attempts.get(0).get("id").asInt();

        // 管理员重置尝试
        mockMvc.perform(post("/drill/admin/tasks/" + taskId + "/reset")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        MvcResult logResult = mockMvc.perform(get("/drill/admin/audit/logs/TASK/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode logs = objectMapper.readTree(logResult.getResponse().getContentAsString()).get("data");
        boolean foundReset = false;
        for (JsonNode logEntry : logs) {
            if ("ATTEMPT_RESET".equals(logEntry.get("action").asText())) {
                foundReset = true;
                break;
            }
        }
        Assertions.assertTrue(foundReset, "应找到 ATTEMPT_RESET 审计日志");
    }

    @Test
    @Order(5)
    void testReviewDecision_CreatesAuditLog() throws Exception {
        // 重新提交（因为被重置了）
        DrillSubmitRequest submitReq = new DrillSubmitRequest();
        submitReq.setTaskId(taskId);
        submitReq.setCheckpointId(checkpointId);
        submitReq.setPayloadSummary("id=1 OR 1=1");
        submitReq.setEvidence("[{\"id\":1,\"username\":\"zhangsan\"},{\"id\":2,\"username\":\"lisi\"}]");
        submitReq.setElapsedSeconds(120);
        submitReq.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isOk());

        // 获取新的尝试记录ID
        MvcResult attemptsResult = mockMvc.perform(get("/drill/student/attempts/" + taskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode attempts = objectMapper.readTree(attemptsResult.getResponse().getContentAsString()).get("data");
        attemptId = attempts.get(0).get("id").asInt();

        // 创建复核单
        ReviewTicketCreateRequest ticketReq = new ReviewTicketCreateRequest();
        ticketReq.setAttemptId(attemptId);
        ticketReq.setReason("审计日志测试");

        MvcResult ticketResult = mockMvc.perform(post("/drill/admin/review/tickets")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ticketReq)))
                .andExpect(status().isOk())
                .andReturn();

        ticketId = objectMapper.readTree(ticketResult.getResponse().getContentAsString())
                .get("data").get("id").asInt();

        // 提交复核决定
        ReviewDecisionRequest decideReq = new ReviewDecisionRequest();
        decideReq.setTicketId(ticketId);
        decideReq.setDecision("approved");
        decideReq.setReviewComment("审计日志测试-确认通过");

        mockMvc.perform(post("/drill/admin/review/decide")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decideReq)))
                .andExpect(status().isOk());

        // 验证审计日志
        MvcResult logResult = mockMvc.perform(get("/drill/admin/audit/logs/REVIEW_TICKET/" + ticketId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode logs = objectMapper.readTree(logResult.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode logEntry : logs) {
            if ("REVIEW_DECISION".equals(logEntry.get("action").asText())) {
                found = true;
                Assertions.assertEquals(1, logEntry.get("actorId").asInt());
                break;
            }
        }
        Assertions.assertTrue(found, "应找到 REVIEW_DECISION 审计日志");
    }

    @Test
    @Order(6)
    void testTaskArchive_CreatesAuditLog() throws Exception {
        mockMvc.perform(delete("/drill/admin/tasks/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        MvcResult logResult = mockMvc.perform(get("/drill/admin/audit/logs/TASK/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode logs = objectMapper.readTree(logResult.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode logEntry : logs) {
            if ("TASK_ARCHIVE".equals(logEntry.get("action").asText())) {
                found = true;
                break;
            }
        }
        Assertions.assertTrue(found, "应找到 TASK_ARCHIVE 审计日志");
    }

    @Test
    @Order(7)
    void testGetAllLogs() throws Exception {
        mockMvc.perform(get("/drill/admin/audit/logs")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").isNotEmpty());
    }

    @Test
    @Order(8)
    void testGuestCannotAccessAuditLogs() throws Exception {
        mockMvc.perform(get("/drill/admin/audit/logs")
                        .header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    void testGuestCannotAccessReviewEndpoints() throws Exception {
        mockMvc.perform(get("/drill/admin/review/tickets")
                        .header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    void testUnauthenticatedCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/drill/admin/audit/logs"))
                .andExpect(status().isUnauthorized());
    }
}
