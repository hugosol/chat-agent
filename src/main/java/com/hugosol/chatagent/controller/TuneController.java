package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.model.FsrsOptimizeLog;
import com.hugosol.chatagent.model.FsrsRescheduleLog;
import com.hugosol.chatagent.repository.FsrsOptimizeLogRepository;
import com.hugosol.chatagent.repository.FsrsRescheduleLogRepository;
import com.hugosol.chatagent.repository.ReviewLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tune")
public class TuneController {

    private static final int MIN_REVIEWS = 512;
    private static final int PAGE_SIZE = 4;

    private final ReviewLogRepository reviewLogRepository;
    private final FsrsOptimizeLogRepository optimizeLogRepository;
    private final FsrsRescheduleLogRepository rescheduleLogRepository;

    public TuneController(ReviewLogRepository reviewLogRepository,
                          FsrsOptimizeLogRepository optimizeLogRepository,
                          FsrsRescheduleLogRepository rescheduleLogRepository) {
        this.reviewLogRepository = reviewLogRepository;
        this.optimizeLogRepository = optimizeLogRepository;
        this.rescheduleLogRepository = rescheduleLogRepository;
    }

    @GetMapping("/review-count")
    public Map<String, Object> reviewCount(@RequestParam String userId) {
        int count = reviewLogRepository.countByUserId(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count);
        result.put("threshold", MIN_REVIEWS);
        return result;
    }

    @GetMapping("/optimize-logs")
    public Page<FsrsOptimizeLog> optimizeLogs(@RequestParam String userId, @RequestParam(defaultValue = "0") int page) {
        return optimizeLogRepository.findByUserIdOrderByStartTimeDesc(userId, PageRequest.of(page, PAGE_SIZE));
    }

    @GetMapping("/reschedule-logs")
    public Page<FsrsRescheduleLog> rescheduleLogs(@RequestParam String userId, @RequestParam(defaultValue = "0") int page) {
        return rescheduleLogRepository.findByUserIdOrderByStartTimeDesc(userId, PageRequest.of(page, PAGE_SIZE));
    }

    private String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
