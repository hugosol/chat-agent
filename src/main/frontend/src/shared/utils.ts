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
