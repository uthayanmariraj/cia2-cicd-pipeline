package com.cia.devopsapp;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Starts the full application context and exercises the HTTP layer. */
@SpringBootTest(properties = {"app.message=test message", "app.version=test-version"})
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void infoReportsConfiguredMessageAndVersion() throws Exception {
        mvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("test message"))
                .andExpect(jsonPath("$.version").value("test-version"))
                .andExpect(jsonPath("$.pod").isNotEmpty());
    }

    @Test
    void gradeReturnsAverageAndLetter() throws Exception {
        mvc.perform(get("/api/grade").param("marks", "90,100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(95.0))
                .andExpect(jsonPath("$.grade").value("O"));
    }

    @Test
    void outOfRangeMarkIsABadRequest() throws Exception {
        mvc.perform(get("/api/grade").param("marks", "150"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("between 0 and 100")));
    }

    @Test
    void nonNumericMarkIsABadRequest() throws Exception {
        mvc.perform(get("/api/grade").param("marks", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Marks must be numbers"));
    }

    @Test
    void homePageIsServed() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void readinessProbeIsUp() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UP")));
    }
}
