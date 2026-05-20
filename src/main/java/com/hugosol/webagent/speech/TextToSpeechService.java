package com.hugosol.webagent.speech;

public interface TextToSpeechService {
    byte[] synthesize(String text);
}
