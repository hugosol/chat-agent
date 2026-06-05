import type { AppStatus } from "../state/chatState";

export function escapeHtml(text: string | null | undefined): string {
  if (!text) return '';
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

export function formatDate(dateStr: string): string {
  if (!dateStr) return '';
  try {
    return new Date(dateStr).toLocaleString();
  } catch {
    return dateStr;
  }
}

export function truncate(text: string | null | undefined, len: number): string {
  if (!text) return '';
  if (text.length <= len) return text;
  return text.substring(0, len) + '...';
}

export function englishOnly(text: string | null | undefined): string {
  if (!text) return '';
  return text.replace(/[^A-Za-z\s]/g, '').replace(/\s+/g, ' ').trim();
}

export function isSessionActive(appStatus: AppStatus): boolean {
  return ["UserTurn", "Processing", "Warning", "Error"].includes(appStatus);
}

export function deriveStatus(appStatus: AppStatus, statusPayload: string | null): { message: string; type: string } {
  switch (appStatus) {
    case "Connecting":
      return { message: "Connecting...", type: "connecting" };
    case "Connected":
      return { message: "Connected", type: "connected" };
    case "UserTurn":
      return { message: "Type your message", type: "userturn" };
    case "Processing":
      return { message: "Processing...", type: "processing" };
    case "Warning":
      return { message: `Warning: ${statusPayload ?? ""}`, type: "warning" };
    case "Error":
      return { message: `Error: ${statusPayload ?? ""}`, type: "error" };
    case "Disconnected":
      return { message: "Disconnected", type: "disconnected" };
  }
}

export function formatInterval(due: string, now: Date): string {
  const dueDate = new Date(due);
  const diffMs = dueDate.getTime() - now.getTime();
  const diffSeconds = Math.round(diffMs / 1000);
  if (diffSeconds < 60) return "<1分钟";
  const diffMinutes = Math.round(diffSeconds / 60);
  if (diffMinutes < 60) return `${diffMinutes}分钟`;
  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}小时`;
  const diffDays = Math.round(diffHours / 24);
  if (diffDays < 30) return `${diffDays}天`;
  const diffMonths = Math.round(diffDays / 30);
  if (diffDays < 365) return `${diffMonths}个月`;
  const diffYears = Math.round(diffDays / 365);
  return `${diffYears}年`;
}
