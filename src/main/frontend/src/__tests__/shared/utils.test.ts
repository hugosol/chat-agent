import { describe, it, expect } from "vitest";
import { escapeHtml, formatDate, truncate, englishOnly } from "../../shared/utils";

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
