export type ErrorType = 'GRAMMAR' | 'WORD_CHOICE' | 'CHINGLISH' | 'PRONUNCIATION' | 'FLUENCY';

export interface CorrectionData {
  type: ErrorType;
  original: string;
  corrected: string;
  explanation: string;
  messageId: number;
}

export interface Tag {
  id: string;
  name: string;
  type: string | null;
}

export interface Card {
  id: string;
  front: string;
  back: string;
  tags: Tag[];
  due: string | null;
  cardState: number;
  createTime: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
}
