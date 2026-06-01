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
