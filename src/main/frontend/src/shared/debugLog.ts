export interface DebugEntry {
  timestamp: Date;
  message: string;
}

type DebugListener = (entry: DebugEntry) => void;

let listeners: DebugListener[] = [];

export function debugLog(message: string): void {
  try {
    const entry: DebugEntry = { timestamp: new Date(), message };
    listeners.forEach((fn) => {
      try { fn(entry); } catch { /* silent */ }
    });
  } catch {
    // silent - debug must never crash main flow
  }
}

export function subscribeDebug(fn: DebugListener): () => void {
  listeners.push(fn);
  return () => {
    listeners = listeners.filter((l) => l !== fn);
  };
}
