export function speakText(text: string, mode?: string): void {
  speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  if (mode === "JAPANESE_BUSINESS") {
    utterance.lang = "ja-JP";
  } else {
    utterance.lang = "en-US";
  }
  utterance.rate = 0.95;
  speechSynthesis.speak(utterance);
}
