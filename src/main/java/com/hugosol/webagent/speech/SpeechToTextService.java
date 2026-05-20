package com.hugosol.webagent.speech;

public interface SpeechToTextService {
    String transcribe(byte[] audioData);
}
