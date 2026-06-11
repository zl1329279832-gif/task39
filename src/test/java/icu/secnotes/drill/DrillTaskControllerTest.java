package icu.secnotes.drill;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.secnotes.pojo.dto.DrillTaskCreateRequest;
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
 * 演练任务管理控制器测试
 * 验证管理员创建/管理任务的权限控制和功能正确性
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrillTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Integer createdTaskId;
    private String adminToken;
    private String guestToken;

    @BeforeAll
    void init() {
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
    void testAdminCreateTask_Success() throws Exception {
        DrillTaskCreateRequest req = buildCreateRequest();

        MvcResult result = mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.task.id").exists())
                .andExpect(jsonPath("$.data.task.title").value("SQL注入与XXE综合演练"))
                .andExpect(jsonPath("$.data.checkpoints").isArray())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        createdTaskId = objectMapper.readTree(json).get("data").get("task").get("id").asInt();
    }

    @Test
    @Order(2)
    void testAdminGetTask_WithCheckpoints() throws Exception {
        mockMvc.perform(get("/drill/admin/tasks/" + createdTaskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.task.id").value(createdTaskId))
                .andExpect(jsonPath("$.data.checkpoints").isArray())
                .andExpect(jsonPath("$.data.checkpoints.length()").value(2));
    }

    @Test
    @Order(3)
    void testAdminListTasks() throws Exception {
        mockMvc.perform(get("/drill/admin/tasks")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").isNotEmpty());
    }

    @Test
    @Order(4)
    void testAdminUpdateTask() throws Exception {
        Map<String, Object> update = new HashMap<>();
        update.put("title", "更新后的演练任务");
        update.put("description", "更新描述");
        update.put("difficulty", "hard");
        update.put("status", "active");

        mockMvc.perform(put("/drill/admin/tasks/" + createdTaskId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(5)
    void testAdminArchiveTask() throws Exception {
        DrillTaskCreateRequest req = buildCreateRequest();
        req.setTitle("待归档任务");

        MvcResult result = mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        int archiveTaskId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("task").get("id").asInt();

        mockMvc.perform(delete("/drill/admin/tasks/" + archiveTaskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(6)
    void testGuestCannotCreateTask_Forbidden() throws Exception {
        DrillTaskCreateRequest req = buildCreateRequest();

        mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void testGuestCannotDeleteTask_Forbidden() throws Exception {
        mockMvc.perform(delete("/drill/admin/tasks/" + createdTaskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    void testGuestCannotViewStats_Forbidden() throws Exception {
        mockMvc.perform(get("/drill/admin/tasks/" + createdTaskId + "/stats")
                        .header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    void testUnauthenticatedRequest_Rejected() throws Exception {
        mockMvc.perform(get("/drill/admin/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(10)
    void testAdminViewRegistry() throws Exception {
        mockMvc.perform(get("/drill/admin/registry")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.SQLI_NUMERIC").exists())
                .andExpect(jsonPath("$.data.XXE_BASIC").exists());
    }

    private DrillTaskCreateRequest buildCreateRequest() {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("SQL注入与XXE综合演练");
        req.setDescription("测试学员对SQL注入和XXE漏洞的利用能力");
        req.setDifficulty("medium");

        List<DrillTaskCreateRequest.CheckpointDef> checkpoints = new ArrayList<>();

        DrillTaskCreateRequest.CheckpointDef cp1 = new DrillTaskCreateRequest.CheckpointDef();
        cp1.setVulnCategory("SQLI_NUMERIC");
        cp1.setCheckpointOrder(1);
        cp1.setMode("EXPLOIT");
        cp1.setMaxScore(100);
        cp1.setMaxHints(3);
        cp1.setTimeLimit(1800);
        checkpoints.add(cp1);

        DrillTaskCreateRequest.CheckpointDef cp2 = new DrillTaskCreateRequest.CheckpointDef();
        cp2.setVulnCategory("XXE_BASIC");
        cp2.setCheckpointOrder(2);
        cp2.setMode("EXPLOIT");
        cp2.setMaxScore(80);
        cp2.setMaxHints(2);
        cp2.setTimeLimit(1200);
        checkpoints.add(cp2);

        req.setCheckpoints(checkpoints);
        return req;
    }
}
