package com.staffsuite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.repository.AuditLogRepository;
import com.staffsuite.repository.UserRepository;
import com.staffsuite.service.PasswordResetNotifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthFlowTests.CapturingNotifierConfig.class)
class AuthFlowTests {

    static final AtomicReference<String> LAST_RESET_TOKEN = new AtomicReference<>();

    @TestConfiguration
    static class CapturingNotifierConfig {
        @Bean
        @Primary
        PasswordResetNotifier capturingNotifier() {
            return (email, token) -> LAST_RESET_TOKEN.set(token);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired AuditLogRepository audits;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        audits.deleteAll();
        users.deleteAll();
        createUser("admin@test.com", "Admin#12345", Role.SYSTEM_ADMIN);
        createUser("emp@test.com", "Employee#123", Role.EMPLOYEE);
    }

    private User createUser(String email, String password, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        return users.save(u);
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn();
    }

    private String token(String email, String password) throws Exception {
        JsonNode body = json.readTree(login(email, password).getResponse().getContentAsString());
        return body.get("token").asString();
    }

    @Test
    void loginReturnsJwtWithoutPasswordAndWritesAuditRow() throws Exception {
        MvcResult res = login("admin@test.com", "Admin#12345");
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        String body = res.getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        assertThat(node.get("token").asString()).isNotBlank();
        assertThat(node.get("user").get("role").asString()).isEqualTo("SYSTEM_ADMIN");
        assertThat(body.toLowerCase()).doesNotContain("password").doesNotContain("reset");
        assertThat(audits.findAll()).anyMatch(a -> "LOGIN".equals(a.getAction()));
    }

    @Test
    void invalidCredentialsReturn401WithStandardShape() throws Exception {
        MvcResult res = login("admin@test.com", "wrong-password");
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        JsonNode node = json.readTree(res.getResponse().getContentAsString());
        assertThat(node.propertyNames()).contains("timestamp", "status", "error", "message", "path");
        assertThat(node.get("path").asString()).isEqualTo("/api/v1/auth/login");
    }

    @Test
    void protectedEndpointRejectsMissingOrBadToken() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotAccessUserManagement() throws Exception {
        String t = token("emp@test.com", "Employee#123");
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + t))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminManagesUsersAndPasswordNeverLeaks() throws Exception {
        String t = "Bearer " + token("admin@test.com", "Admin#12345");

        MvcResult created = mvc.perform(post("/api/v1/users").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@test.com\",\"password\":\"NewUser#123\",\"role\":\"MANAGER\"}"))
                .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/v1/users").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@test.com\",\"password\":\"NewUser#123\",\"role\":\"MANAGER\"}"))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/v1/users/" + id + "/role").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"HR_ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("HR_ADMIN"));

        mvc.perform(put("/api/v1/users/" + id + "/status").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        // deactivated user can no longer log in
        assertThat(login("new@test.com", "NewUser#123").getResponse().getStatus()).isEqualTo(401);

        String list = mvc.perform(get("/api/v1/users").header("Authorization", t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(list.toLowerCase()).doesNotContain("password").doesNotContain("hash");
    }

    @Test
    void adminCannotChangeOwnRole() throws Exception {
        String t = "Bearer " + token("admin@test.com", "Admin#12345");
        long adminId = users.findByEmailIgnoreCase("admin@test.com").orElseThrow().getId();
        mvc.perform(put("/api/v1/users/" + adminId + "/role").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordAndLogout() throws Exception {
        String t = "Bearer " + token("emp@test.com", "Employee#123");
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"nope\",\"newPassword\":\"Another#1234\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Employee#123\",\"newPassword\":\"Another#1234\"}"))
                .andExpect(status().isOk());
        assertThat(login("emp@test.com", "Employee#123").getResponse().getStatus()).isEqualTo(401);
        assertThat(login("emp@test.com", "Another#1234").getResponse().getStatus()).isEqualTo(200);

        mvc.perform(post("/api/v1/auth/logout").header("Authorization", t)).andExpect(status().isOk());
        assertThat(audits.findAll()).anyMatch(a -> "LOGOUT".equals(a.getAction()));
    }

    @Test
    void passwordResetFlow() throws Exception {
        LAST_RESET_TOKEN.set(null);
        mvc.perform(post("/api/v1/auth/reset-password-request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"emp@test.com\"}")).andExpect(status().isOk());
        String token = LAST_RESET_TOKEN.get();
        assertThat(token).isNotBlank();

        // unknown accounts get the same response and no token
        LAST_RESET_TOKEN.set(null);
        mvc.perform(post("/api/v1/auth/reset-password-request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ghost@test.com\"}")).andExpect(status().isOk());
        assertThat(LAST_RESET_TOKEN.get()).isNull();

        mvc.perform(post("/api/v1/auth/reset-password-confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"bogus\",\"newPassword\":\"Reset#12345\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/reset-password-confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"Reset#12345\"}"))
                .andExpect(status().isOk());
        // token is single use
        mvc.perform(post("/api/v1/auth/reset-password-confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"Reset#99999\"}"))
                .andExpect(status().isBadRequest());
        assertThat(login("emp@test.com", "Reset#12345").getResponse().getStatus()).isEqualTo(200);
    }
}
