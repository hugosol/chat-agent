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
  totalNewCards: number;
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
  enhancement?: EnhancementData | null;
}

export interface EnhancementData {
  movieQuote?: {
    movieTitle: string;
    imdbId: string;
    quote: string;
    timestamp: string;
  } | null;
  sceneSummary?: string | null;
  etymology?: string | null;
}

export type PreviewInfo = Record<string, {
  stability: number;
  difficulty: number;
  state: number;
  step: number;
  due: string;
  reps: number;
  lapses: number;
  lastReview: string | null;
  elapsedDays: number;
}>;

export interface CardResponse {
  card: ReviewCard | null;
  stats: ReviewStats;
  preview?: PreviewInfo;
}
