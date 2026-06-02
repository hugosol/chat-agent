package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.dto.ModeResponse;
import com.hugosol.chatagent.model.AgentMode;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ModeController {

    @GetMapping("/modes")
    public List<ModeResponse> getModes() {
        return Arrays.stream(AgentMode.values())
                .map(m -> new ModeResponse(m.name(), m.getDisplayName()))
                .toList();
    }
}
