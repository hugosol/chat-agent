package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.model.FsrsOptimizeLog;
import com.hugosol.chatagent.model.FsrsRescheduleLog;
import com.hugosol.chatagent.model.OptimizeStatus;
import com.hugosol.chatagent.model.RescheduleStatus;
import com.hugosol.chatagent.model.TriggerType;
import com.hugosol.chatagent.repository.FsrsOptimizeLogRepository;
import com.hugosol.chatagent.repository.FsrsRescheduleLogRepository;
import com.hugosol.chatagent.repository.ReviewLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TuneController.class)
class TuneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewLogRepository reviewLogRepository;

    @MockitoBean
    private FsrsOptimizeLogRepository optimizeLogRepository;

    @MockitoBean
    private FsrsRescheduleLogRepository rescheduleLogRepository;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void reviewCount_returnsCountAndThreshold() throws Exception {
        when(reviewLogRepository.countByUserId("admin")).thenReturn(850);

        mockMvc.perform(get("/api/tune/review-count")
                        .param("userId", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(850)))
                .andExpect(jsonPath("$.threshold", is(512)));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void reviewCount_regularUserCanQueryOwn() throws Exception {
        when(reviewLogRepository.countByUserId("user1")).thenReturn(200);

        mockMvc.perform(get("/api/tune/review-count")
                        .param("userId", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(200)));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void optimizeLogs_returnsPaginatedResults() throws Exception {
        FsrsOptimizeLog log = new FsrsOptimizeLog();
        log.setUserId("admin");
        log.setTriggerType(TriggerType.MANUAL);
        log.setStatus(OptimizeStatus.SUCCESS);
        log.setNonSameDayReviews(600);
        log.setFinalLoss(0.35);
        log.setDefaultLoss(0.50);
        log.setDurationMs(8000);
        log.setStartTime(Instant.parse("2026-06-06T10:00:00Z"));
        when(optimizeLogRepository.findByUserIdOrderByStartTimeDesc(eq("admin"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        mockMvc.perform(get("/api/tune/optimize-logs")
                        .param("userId", "admin")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].triggerType", is("MANUAL")))
                .andExpect(jsonPath("$.content[0].status", is("SUCCESS")))
                .andExpect(jsonPath("$.content[0].nonSameDayReviews", is(600)));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void rescheduleLogs_returnsPaginatedResults() throws Exception {
        FsrsRescheduleLog log = new FsrsRescheduleLog();
        log.setUserId("admin");
        log.setTriggerType(TriggerType.MANUAL);
        log.setStatus(RescheduleStatus.SUCCESS);
        log.setRescheduledCards(120);
        log.setDurationMs(1500);
        log.setStartTime(Instant.parse("2026-06-06T10:00:05Z"));
        when(rescheduleLogRepository.findByUserIdOrderByStartTimeDesc(eq("admin"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        mockMvc.perform(get("/api/tune/reschedule-logs")
                        .param("userId", "admin")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].triggerType", is("MANUAL")))
                .andExpect(jsonPath("$.content[0].status", is("SUCCESS")))
                .andExpect(jsonPath("$.content[0].rescheduledCards", is(120)));
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void regularUser_canQueryOwnData() throws Exception {
        when(optimizeLogRepository.findByUserIdOrderByStartTimeDesc(eq("user1"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/tune/optimize-logs")
                        .param("userId", "user1")
                        .param("page", "0"))
                .andExpect(status().isOk());
    }
}
