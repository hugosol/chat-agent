package com.hugosol.webagent.dto;

import com.hugosol.webagent.model.ErrorType;

import java.io.Serializable;

public class CorrectionData implements Serializable {
    private ErrorType type;
    private String original;
    private String corrected;
    private String explanation;
    private int messageId;

    public CorrectionData() {}

    public CorrectionData(ErrorType type, String original, String corrected, String explanation) {
        this.type = type;
        this.original = original;
        this.corrected = corrected;
        this.explanation = explanation;
    }

    public ErrorType getType() { return type; }
    public void setType(ErrorType type) { this.type = type; }

    public String getOriginal() { return original; }
    public void setOriginal(String original) { this.original = original; }

    public String getCorrected() { return corrected; }
    public void setCorrected(String corrected) { this.corrected = corrected; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public int getMessageId() { return messageId; }
    public void setMessageId(int messageId) { this.messageId = messageId; }
}
