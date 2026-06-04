export type ReviewMode = "STANDARD" | "REVIEW_ONLY" | "NEW_ONLY" | "CRAM";

export type ReviewStage = "deck-picker" | "reviewing" | "complete";

export interface DeckInfo {
  id: string;
  name: string;
  type: string;
  cardCount: number;
}

export interface ReviewStats {
  reviewedToday: number;
  remaining: number;
  learnedToday: number;
  dailyLimit: number;
  nextDueAt: string | null;
}

export interface ReviewCard {
  id: string;
  front: string;
  back: string;
  tags: { id: string; name: string; type: string | null }[];
  due: string | null;
  cardState: number;
  step: number;
  stability: number;
  difficulty: number;
  reps: number;
  lapses: number;
  lastReview: string | null;
  firstReviewDate: string | null;
  createTime: string | null;
}

export interface CardResponse {
  card: ReviewCard | null;
  stats: ReviewStats;
}
