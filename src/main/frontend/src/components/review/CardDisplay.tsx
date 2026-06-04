import React, { useState } from "react";
import { speakText } from "../../shared/tts";
import { englishOnly } from "../../shared/utils";
import styles from "./CardDisplay.module.css";

interface Props {
  front: string;
  back: string;
}

export function CardDisplay({ front, back }: Props): React.ReactElement {
  const [flipped, setFlipped] = useState(false);

  const handleFlip = () => {
    setFlipped(true);
  };

  const showTtsFront = englishOnly(front).length > 0;
  const showTtsBack = englishOnly(back).length > 0;

  const renderText = (text: string) => {
    const lines = text.split("\n");
    return lines.map((line, i) => (
      <span key={i}>
        {line}
        {i < lines.length - 1 && <br />}
      </span>
    ));
  };

  return (
    <div className={styles.cardArea}>
      <div
        data-testid="flip-card-btn"
        className={`${styles.card} ${flipped ? styles.cardFlipped : ""}`}
        onClick={!flipped ? handleFlip : undefined}
      >
        <div className={styles.cardInner}>
          <div className={styles.cardFront} data-testid="card-front">
            <span className={styles.cardText}>{renderText(front)}</span>
            {showTtsFront && (
              <button
                data-testid="tts-btn-front"
                className={styles.ttsBtn}
                onClick={(e) => { e.stopPropagation(); speakText(englishOnly(front)); }}
              >
                {"\uD83D\uDD0A"}
              </button>
            )}
          </div>
          {flipped && (
            <div className={styles.cardBack} data-testid="card-back">
              <span className={styles.cardText}>{renderText(back)}</span>
              {showTtsBack && (
                <button
                  data-testid="tts-btn-back"
                  className={styles.ttsBtn}
                  onClick={(e) => { e.stopPropagation(); speakText(englishOnly(back)); }}
                >
                  {"\uD83D\uDD0A"}
                </button>
              )}
            </div>
          )}
        </div>
        {!flipped && (
          <p className={styles.tapHint}>Tap to reveal</p>
        )}
      </div>
    </div>
  );
}
