package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ReportAgent;
import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.graph.CorrectionData;
import com.hugosol.webagent.graph.MessageData;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReportGenerator {

    private final ReportAgent reportAgent;

    public ReportGenerator(ReportAgent reportAgent) {
        this.reportAgent = reportAgent;
    }

    public ReportResult generate(List<MessageData> messages, List<CorrectionData> corrections) {
        return reportAgent.generate(messages, corrections);
    }
}
