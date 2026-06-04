import React, { useState } from "react";
import { Header } from "../Header/Header";
import { DeckPicker } from "./DeckPicker";
import { ReviewPage } from "./ReviewPage";
import { CompletePage } from "./CompletePage";
import type { ReviewStage, ReviewMode, DeckInfo, ReviewStats, ReviewCard } from "./reviewTypes";

export function ReviewApp(): React.ReactElement {
  const [stage, setStage] = useState<ReviewStage>("deck-picker");
  const [selectedDeck, setSelectedDeck] = useState<DeckInfo | null>(null);
  const [selectedMode, setSelectedMode] = useState<ReviewMode>("STANDARD");
  const [dailyLimit, setDailyLimit] = useState(20);
  const [stats, setStats] = useState<ReviewStats | null>(null);
  const [completeCard, setCompleteCard] = useState<ReviewCard | null>(null);

  const handleStartReview = (deck: DeckInfo, mode: ReviewMode, limit: number) => {
    setSelectedDeck(deck);
    setSelectedMode(mode);
    setDailyLimit(limit);
    setStage("reviewing");
  };

  const handleReviewComplete = (finalStats: ReviewStats, lastCard: ReviewCard | null) => {
    setStats(finalStats);
    setCompleteCard(lastCard);
    setStage("complete");
  };

  const handleBackToPicker = () => {
    setStage("deck-picker");
    setSelectedDeck(null);
    setStats(null);
    setCompleteCard(null);
  };

  const renderContent = () => {
    if (stage === "reviewing" && selectedDeck) {
      return (
        <ReviewPage
          deck={selectedDeck}
          mode={selectedMode}
          limit={dailyLimit}
          onComplete={handleReviewComplete}
          onBack={handleBackToPicker}
        />
      );
    }

    if (stage === "complete" && stats) {
      return <CompletePage stats={stats} lastCard={completeCard} onBack={handleBackToPicker} />;
    }

    return <DeckPicker onStart={handleStartReview} />;
  };

  return (
    <>
      <Header />
      {renderContent()}
    </>
  );
}
