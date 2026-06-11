package icu.secnotes.drill;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.secnotes.pojo.dto.DrillTaskCreateRequest;
import icu.secnotes.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DrillTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String guestToken;

    @BeforeEach
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

    private DrillTaskCreateRequest buildCreateRequest() {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("SQL注入综合演练");
        req.setDescription("包含数字型和字符型SQL注入靶点");
        req.setDifficulty("medium");

        List<DrillTaskCreateRequest.CheckpointDef> cps = new ArrayList<>();

        DrillTaskCreateRequest.CheckpointDef cp1 = new DrillTaskCreateRequest.CheckpointDef();
        cp1.setVulnCategory("SQLI_NUMERIC");
        cp1.setCheckpointOrder(1);
        cp1.setMode("EXPLOIT");
        cp1.setMaxScore(100);
        cp1.setMaxHints(3);
        cp1.setTimeLimit(1800);
        cp1.setHintContent("尝试在id参数中使用 OR 1=1");
        cps.add(cp1);

        DrillTaskCreateRequest.CheckpointDef cp2 = new DrillTaskCreateRequest.CheckpointDef();
        cp2.setVulnCategory("SQLI_STRING");
        cp2.setCheckpointOrder(2);
        cp2.setMode("EXPLOIT");
        cp2.setMaxScore(100);
        cp2.setMaxHints(3);
        cp2.setTimeLimit(1800);
        cps.add(cp2);

        req.setCheckpoints(cps);
        return req;
    }

    private Integer createTaskAndGetId() throws Exception {
        DrillTaskCreateRequest req = buildCreateRequest();
        MvcResult result = mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        Map<String, Object> task = (Map<String, Object>) data.get("task");
        return (Integer) task.get("id");
    }

    @Test
    void testAdminCreateTask_Success() throws Exception {
        DrillTaskCreateRequest req = buildCreateRequest();

        mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.task.id").exists())
                .andExpect(jsonPath("$.data.task.title").value("SQL注入综合演练"))
                .andExpect(jsonPath("$.data.checkpoints").isArray())
                .andExpect(jsonPath("$.data.checkpoints.length()").value(2));
    }

    @Test
    void testAdminGetTask_WithCheckpoints() throws Exception {
        Integer taskId = createTaskAndGetId();

        mockMvc.perform(get("/drill/admin/tasks/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.task.id").value(taskId))
                .andExpect(jsonPath("$.data.checkpoints").isArray())
                .andExpect(jsonPath("$.data.checkpoints.length()").value(2));
    }

    @Test
    void testAdminListTasks() throws Exception {
        createTaskAndGetId();

        mockMvc.perform(get("/drill/admin/tasks")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void testAdminUpdateTask() throws Exception {
        Integer taskId = createTaskAndGetId();

        Map<String, String> update = new HashMap<>();
        update.put("title", "更新后的标题");
        update.put("description", "更新后的描述");
        update.put("difficulty", "hard");

        mockMvc.perform(put("/drill/admin/tasks/" + taskId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void testAdminArchiveTask() throws Exception {
        Integer taskId = createTaskAndGetId();

        mockMvc.perform(delete("/drill/admin/tasks/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Verify archived
        mockMvc.perform(get("/drill/admin/tasks/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("archived"));
    }

    @Test
    void testGuestCannotCreateTask_Forbidden() throws Exception {
        DrillTaskCreateRequest req = buildCreateRequest();

        mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGuestCannotDeleteTask_Forbidden() throws Exception {
        mockMvc.perform(delete("/drill/admin/tasks/1")
                        .header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGuestCannotViewStats_Forbidden() throws Exception {
        mockMvc.perform(get("/drill/admin/tasks/1/stats")
                        .header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUnauthenticatedRequest_Rejected() throws Exception {
        DrillTaskCreateRequest req = buildCreateRequest();

        mockMvc.perform(post("/drill/admin/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAdminViewRegistry() throws Exception {
        mockMvc.perform(get("/drill/admin/registry")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.SQLI_NUMERIC").exists())
                .andExpect(jsonPath("$.data.XXE_BASIC").exists());
    }
}
