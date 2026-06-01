export type ErrorType = 'GRAMMAR' | 'WORD_CHOICE' | 'CHINGLISH' | 'PRONUNCIATION' | 'FLUENCY';

export interface CorrectionData {
  type: ErrorType;
  original: string;
  corrected: string;
  explanation: string;
  messageId: number;
}
