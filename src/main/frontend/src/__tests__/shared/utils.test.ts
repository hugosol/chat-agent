import { describe, it, expect } from "vitest";
import { escapeHtml, formatDate, truncate, englishOnly, isSessionActive, deriveStatus, formatInterval } from "../../shared/utils";

describe("escapeHtml", () => {
  it("escapes < > and & to HTML entities via DOM textContent", () => {
    const result = escapeHtml('<script>alert("xss")</script>');
    expect(result).not.toContain("<");
    expect(result).not.toContain(">");
    expect(result).toContain("&lt;");
    expect(result).toContain("&gt;");
  });

  it("returns empty string for null or undefined", () => {
    expect(escapeHtml(null)).toBe("");
    expect(escapeHtml(undefined)).toBe("");
    expect(escapeHtml("")).toBe("");
  });

  it("returns plain text unchanged", () => {
    const result = escapeHtml("Hello, World!");
    expect(result).toBe("Hello, World!");
  });
});

describe("formatDate", () => {
  it("formats a valid date string to locale string", () => {
    const result = formatDate("2025-06-01T12:00:00Z");
    expect(result).toContain("2025");
    expect(result.length).toBeGreaterThan(0);
  });

  it("handles empty string gracefully", () => {
    const result = formatDate("");
    expect(typeof result).toBe("string");
  });
});

describe("truncate", () => {
  it("returns text unchanged when shorter than limit", () => {
    expect(truncate("Hello", 10)).toBe("Hello");
  });

  it("truncates text longer than limit with ellipsis", () => {
    expect(truncate("Hello, World!", 5)).toBe("Hello...");
  });

  it("returns empty string for null or empty input", () => {
    expect(truncate("", 10)).toBe("");
    expect(truncate(null as unknown as string, 10)).toBe("");
  });
});

describe("englishOnly", () => {
  it("removes non-English characters keeping letters and spaces", () => {
    const result = englishOnly("Hello 你好 World！测试");
    expect(result).toBe("Hello World");
  });

  it("collapses multiple spaces into one", () => {
    const result = englishOnly("Hello     World");
    expect(result).toBe("Hello World");
  });

  it("returns empty string for empty input", () => {
    expect(englishOnly("")).toBe("");
    expect(englishOnly(null as unknown as string)).toBe("");
  });
});

describe("isSessionActive", () => {
  it("returns true for UserTurn", () => {
    expect(isSessionActive("UserTurn")).toBe(true);
  });

  it("returns true for Processing", () => {
    expect(isSessionActive("Processing")).toBe(true);
  });

  it("returns true for Warning", () => {
    expect(isSessionActive("Warning")).toBe(true);
  });

  it("returns true for Error", () => {
    expect(isSessionActive("Error")).toBe(true);
  });

  it("returns false for Connecting", () => {
    expect(isSessionActive("Connecting")).toBe(false);
  });

  it("returns false for Connected", () => {
    expect(isSessionActive("Connected")).toBe(false);
  });

  it("returns false for Disconnected", () => {
    expect(isSessionActive("Disconnected")).toBe(false);
  });
});

describe("deriveStatus", () => {
  it('returns Connecting message', () => {
    const result = deriveStatus("Connecting", null);
    expect(result.type).toBe("connecting");
    expect(result.message).toContain("Connecting");
  });

  it('returns Connected message', () => {
    const result = deriveStatus("Connected", null);
    expect(result.type).toBe("connected");
    expect(result.message).toContain("Connected");
  });

  it('returns UserTurn message', () => {
    const result = deriveStatus("UserTurn", null);
    expect(result.type).toBe("userturn");
    expect(result.message).toContain("Type");
  });

  it('returns Processing message', () => {
    const result = deriveStatus("Processing", null);
    expect(result.type).toBe("processing");
    expect(result.message).toContain("Processing");
  });

  it('returns Warning with custom payload', () => {
    const result = deriveStatus("Warning", "Token usage at 80%");
    expect(result.type).toBe("warning");
    expect(result.message).toContain("Token usage at 80%");
  });

  it('returns Error with custom payload', () => {
    const result = deriveStatus("Error", "Connection failed");
    expect(result.type).toBe("error");
    expect(result.message).toContain("Connection failed");
  });

  it('returns Disconnected message', () => {
    const result = deriveStatus("Disconnected", null);
    expect(result.type).toBe("disconnected");
    expect(result.message).toContain("Disconnected");
  });
});

describe("formatInterval", () => {
  const now = new Date("2026-06-05T12:00:00Z");

  it('returns "<1分钟" for less than 60 seconds', () => {
    const due = new Date("2026-06-05T12:00:30Z").toISOString();
    expect(formatInterval(due, now)).toBe("<1分钟");
  });

  it('returns "X分钟" for less than 60 minutes', () => {
    const due = new Date("2026-06-05T12:10:00Z").toISOString();
    expect(formatInterval(due, now)).toBe("10分钟");
  });

  it('returns "X小时" for less than 24 hours', () => {
    const due = new Date("2026-06-05T20:00:00Z").toISOString();
    expect(formatInterval(due, now)).toBe("8小时");
  });

  it('returns "X天" for less than 30 days', () => {
    const due = new Date("2026-06-10T12:00:00Z").toISOString();
    expect(formatInterval(due, now)).toBe("5天");
  });

  it('returns "X个月" for less than 365 days', () => {
    const due = new Date("2026-08-05T12:00:00Z").toISOString();
    expect(formatInterval(due, now)).toBe("2个月");
  });

  it('returns "X年" for 365 days or more', () => {
    const due = new Date("2027-06-05T12:00:00Z").toISOString();
    expect(formatInterval(due, now)).toBe("1年");
  });
});
