package com.octapoint.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.octapoint.core.security.RateLimitFilter;
import com.octapoint.core.shared.ApiException;
import com.octapoint.core.shared.WebhookUrlValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:octapoint_sec_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class SecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ssrfValidatorBlocksMetadataInternalIpsAndDisallowedSchemes() {
        assertThrows(ApiException.class,
                () -> WebhookUrlValidator.validate("http://169.254.169.254/latest/meta-data/", true));
        assertThrows(ApiException.class,
                () -> WebhookUrlValidator.validate("http://10.0.0.1/hook", true));
        assertThrows(ApiException.class,
                () -> WebhookUrlValidator.validate("http://192.168.1.1/hook", true));
        assertThrows(ApiException.class,
                () -> WebhookUrlValidator.validate("http://signer:3001/internal/issue", true));
        assertThrows(ApiException.class,
                () -> WebhookUrlValidator.validate("file:///etc/passwd", true));
        assertThrows(ApiException.class,
                () -> WebhookUrlValidator.validate("http://127.0.0.1:8080/hook", false));
    }

    @Test
    void passwordValidationAndWebhookSecretNonExposureAndFilterBypassHardening() throws Exception {
        // 1. Register without password or short password must fail with 400 INVALID_PASSWORD
        mockMvc.perform(post("/api/portal/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "businessName", "No Pass Shop",
                                "email", "nopass@sec-test.io"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"));

        mockMvc.perform(post("/api/portal/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "businessName", "Short Pass Shop",
                                "email", "shortpass@sec-test.io",
                                "password", "1234567"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"));

        // 2. Register valid merchant and approve via admin
        MvcResult regRes = mockMvc.perform(post("/api/portal/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "businessName", "Sec Shop",
                                "name", "Sec Owner",
                                "email", "owner@sec-test.io",
                                "password", "StrongPass123!"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String tenantId = objectMapper.readTree(regRes.getResponse().getContentAsString()).get("tenantId").asText();

        String adminToken = login("admin@octapoint.io", "admin123");
        mockMvc.perform(patch("/api/admin/tenants/" + tenantId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk());

        String merchantToken = login("owner@sec-test.io", "StrongPass123!");

        // 3. Path normalization & RBAC checks + Security Headers
        mockMvc.perform(get("/api//admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));

        mockMvc.perform(get("/api//admin/dashboard")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN_ADMIN_ONLY"));

        mockMvc.perform(get("/api/admin/..;/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PATH"));

        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));

        // 4. SSRF blocking on /api/portal/webhook
        mockMvc.perform(put("/api/portal/webhook")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "webhookUrl", "http://169.254.169.254/latest/meta-data/",
                                "webhookSecret", "whsec_secret_value"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_WEBHOOK_URL"));

        // 5. Valid webhook update must not expose webhookSecret in response
        mockMvc.perform(put("/api/portal/webhook")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "webhookUrl", "http://127.0.0.1:9999/hook",
                                "webhookSecret", "whsec_do_not_leak"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookUrl").value("http://127.0.0.1:9999/hook"))
                .andExpect(jsonPath("$.hasSecret").value(true))
                .andExpect(jsonPath("$.webhookSecret").doesNotExist());
    }

    @Test
    void loginRateLimiterCannotBeBypassedWithSpoofedApiKeyHeader() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(120, 2);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/portal/auth/login");
            req.setRemoteAddr("10.20.30.40");
            req.addHeader("X-OP-Key", "spoofed-key-" + i);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus());
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/portal/auth/login");
        blockedReq.setRemoteAddr("10.20.30.40");
        blockedReq.addHeader("X-OP-Key", "spoofed-key-999");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, new MockFilterChain());
        assertEquals(429, blockedRes.getStatus());
    }

    private String login(String email, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }
}
