package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.dto.OptimizeProgress;
import com.hugosol.chatagent.service.FsrsOptimizeService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/fsrs")
public class FsrsOptimizeController {

    private final FsrsOptimizeService optimizeService;

    public FsrsOptimizeController(FsrsOptimizeService optimizeService) {
        this.optimizeService = optimizeService;
    }

    @PostMapping("/optimize")
    public ResponseEntity<Map<String, Object>> startOptimize() {
        String userId = getUserId();
        String taskId = optimizeService.startOptimize(userId);

        OptimizeProgress progress = optimizeService.getProgress(taskId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);

        if (progress != null && "SKIPPED".equals(progress.status())) {
            response.put("status", "skipped");
            response.put("reason", progress.reason());
        } else {
            response.put("status", "running");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/optimize/status")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam String taskId) {
        OptimizeProgress progress = optimizeService.getProgress(taskId);

        if (progress == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("status", progress.status().toLowerCase());

        if ("RUNNING".equals(progress.status())) {
            Map<String, Object> prog = new LinkedHashMap<>();
            prog.put("epoch", progress.epoch());
            prog.put("batch", progress.batch());
            prog.put("totalBatches", progress.totalBatches());
            prog.put("currentLoss", progress.currentLoss());
            response.put("progress", prog);
        }

        if (progress.result() != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("weights", progress.result().weights());
            result.put("finalLoss", progress.result().finalLoss());
            result.put("iterations", progress.result().iterations());
            result.put("durationMs", progress.result().durationMs());
            response.put("result", result);
        }

        if (progress.reason() != null) {
            response.put("reason", progress.reason());
        }

        return ResponseEntity.ok(response);
    }

    private String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }
}
