import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, fireEvent, waitFor } from "@testing-library/react";
import { Header } from "../../components/Header/Header";

function setPath(path: string): void {
  Object.defineProperty(window, "location", {
    value: { pathname: path },
    writable: true,
  });
}

describe("Header", () => {
  let originalFetch: typeof global.fetch;

  beforeEach(() => {
    originalFetch = global.fetch;
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ username: "testuser", admin: false }),
    } as Response);
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it("renders username button after fetching user info", async () => {
    setPath("/");
    const { findByTestId } = render(<Header />);
    const btn = await findByTestId("nav-user-btn");
    expect(btn.textContent).toBe("\u25C1 testuser");
  });

  it("renders logout form inside user button", async () => {
    setPath("/");
    const { findByTestId } = render(<Header />);
    const btn = await findByTestId("nav-user-btn");
    const form = btn.querySelector("form");
    expect(form).not.toBeNull();
    expect(form!.getAttribute("action")).toBe("/logout");
    expect(form!.method).toBe("post");
    const logoutBtn = form!.querySelector("button");
    expect(logoutBtn).not.toBeNull();
    expect(logoutBtn!.getAttribute("aria-label")).toBe("Logout");
  });

  it("shows placeholder text while loading", () => {
    global.fetch = vi.fn().mockImplementation(() => new Promise(() => {}));
    setPath("/");
    const { getByTestId } = render(<Header />);
    const btn = getByTestId("nav-user-btn");
    expect(btn.textContent).toBe("\u25C1 ...");
  });

  it("shows fallback Logout text on fetch error", async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error("network"));
    setPath("/");
    const { findByTestId } = render(<Header />);
    const btn = await findByTestId("nav-user-btn");
    expect(btn.textContent).toBe("\u25C1 Logout");
  });

  it("renders a hamburger menu button", () => {
    const { getByTestId } = render(<Header />);
    const btn = getByTestId("nav-menu-btn");
    expect(btn.textContent).toBe("\u2630");
    expect(btn.tagName).toBe("BUTTON");
  });

  it("opens sidebar when hamburger button is clicked", () => {
    const { getByTestId } = render(<Header />);
    const btn = getByTestId("nav-menu-btn");
    fireEvent.click(btn);
    const sidebar = getByTestId("nav-sidebar");
    expect(sidebar.getAttribute("aria-expanded")).toBe("true");
  });

  it("closes sidebar when close button is clicked", () => {
    const { getByTestId } = render(<Header />);
    const menuBtn = getByTestId("nav-menu-btn");
    fireEvent.click(menuBtn);
    const closeBtn = getByTestId("nav-sidebar-close");
    fireEvent.click(closeBtn);
    const sidebar = getByTestId("nav-sidebar");
    expect(sidebar.getAttribute("aria-expanded")).toBe("false");
  });

  it("highlights Chat link on chat page path /", async () => {
    setPath("/");
    const { findAllByTestId } = render(<Header />);
    const links = await findAllByTestId("nav-link");
    const chatLink = links.find((l) => l.textContent?.includes("Chat"));
    expect(chatLink?.getAttribute("data-active")).toBe("true");
  });

  it("highlights Manage link on manage page path", async () => {
    setPath("/manage/index.html");
    const { findAllByTestId } = render(<Header />);
    const links = await findAllByTestId("nav-link");
    const manageLink = links.find((l) => l.textContent?.includes("Manage"));
    expect(manageLink?.getAttribute("data-active")).toBe("true");
  });

  it("does not render token bar on manage page", () => {
    setPath("/manage/index.html");
    const { queryByTestId } = render(<Header />);
    expect(queryByTestId("token-bar-fill")).toBeNull();
  });

  it("renders token bar on chat page path /", () => {
    setPath("/");
    const { getByTestId } = render(<Header />);
    expect(getByTestId("token-bar-fill")).toBeInTheDocument();
  });

  it("shows green bar when tokenPercent is 0", () => {
    setPath("/");
    const { getByTestId } = render(<Header tokenPercent={0} />);
    const bar = getByTestId("token-bar-fill");
    expect(bar.style.width).toBe("0%");
    expect(bar.style.backgroundColor).toBe("rgb(39, 174, 96)");
  });

  it("shows yellow bar when tokenPercent is 50", () => {
    setPath("/");
    const { getByTestId } = render(<Header tokenPercent={50} />);
    const bar = getByTestId("token-bar-fill");
    expect(bar.style.width).toBe("50%");
    expect(bar.style.backgroundColor).toBe("rgb(243, 156, 18)");
  });

  it("shows red bar when tokenPercent is 80", () => {
    setPath("/");
    const { getByTestId } = render(<Header tokenPercent={80} />);
    const bar = getByTestId("token-bar-fill");
    expect(bar.style.width).toBe("80%");
    expect(bar.style.backgroundColor).toBe("rgb(231, 76, 60)");
  });

  it("calls onTogglePanel with 'menu' when hamburger is clicked", () => {
    const onTogglePanel = vi.fn();
    const { getByTestId } = render(<Header onTogglePanel={onTogglePanel} />);
    fireEvent.click(getByTestId("nav-menu-btn"));
    expect(onTogglePanel).toHaveBeenCalledWith("menu");
  });

  it("calls onTogglePanel with 'menu' when close button is clicked", () => {
    const onTogglePanel = vi.fn();
    const { getByTestId } = render(
      <Header activePanel="menu" onTogglePanel={onTogglePanel} />
    );
    fireEvent.click(getByTestId("nav-sidebar-close"));
    expect(onTogglePanel).toHaveBeenCalledWith("menu");
  });

  it("shows sidebar open when activePanel is 'menu'", () => {
    const { getByTestId } = render(
      <Header activePanel="menu" onTogglePanel={() => {}} />
    );
    expect(getByTestId("nav-sidebar").getAttribute("aria-expanded")).toBe("true");
  });

  it("falls back to local state when onTogglePanel is not provided", () => {
    const { getByTestId } = render(<Header />);
    fireEvent.click(getByTestId("nav-menu-btn"));
    expect(getByTestId("nav-sidebar").getAttribute("aria-expanded")).toBe("true");
  });

  it("renders settings nav link with correct href", async () => {
    setPath("/");
    const { findByTestId } = render(<Header />);
    const link = (await findByTestId("nav-settings")) as HTMLAnchorElement;
    expect(link.getAttribute("href")).toBe("/settings/index.html");
    expect(link.textContent).toContain("设置");
  });

  it("highlights Settings link on settings page path", async () => {
    setPath("/settings/index.html");
    const { findByTestId } = render(<Header />);
    const link = await findByTestId("nav-settings");
    expect(link.getAttribute("data-active")).toBe("true");
  });

  it("renders Profile nav link with correct href", async () => {
    setPath("/");
    const { findByTestId } = render(<Header />);
    const link = (await findByTestId("nav-profile-link")) as HTMLAnchorElement;
    expect(link.getAttribute("href")).toBe("/profile/index.html");
    expect(link.textContent).toContain("Profile");
  });

  it("highlights Profile link on profile page path", async () => {
    setPath("/profile/index.html");
    const { findByTestId } = render(<Header />);
    const link = await findByTestId("nav-profile-link");
    expect(link.getAttribute("data-active")).toBe("true");
  });

  it("renders Tune nav link with correct href", async () => {
    setPath("/");
    const { findByTestId } = render(<Header />);
    const link = (await findByTestId("nav-tune-link")) as HTMLAnchorElement;
    expect(link.getAttribute("href")).toBe("/tune/index.html");
    expect(link.textContent).toContain("Tune");
  });

  it("highlights Tune link on tune page path", async () => {
    setPath("/tune/index.html");
    const { findByTestId } = render(<Header />);
    const link = await findByTestId("nav-tune-link");
    expect(link.getAttribute("data-active")).toBe("true");
  });
});

describe("Header timezone auto-detection", () => {
  let originalFetch: typeof global.fetch;

  const detectedOffset = -(new Date().getTimezoneOffset() / 60);

  function setupTzFetch(utcOffset: number | null) {
    global.fetch = vi.fn((url: string, init?: RequestInit) => {
      if (url === "/api/user/me") {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ username: "testuser" }),
        } as Response);
      }
      if (url === "/api/user/preferences" && (!init || init.method === undefined)) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ utcOffset }),
        } as Response);
      }
      if (url === "/api/user/preferences" && init?.method === "PUT") {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(JSON.parse(init.body as string)),
        } as Response);
      }
      return Promise.resolve({ ok: true, json: () => Promise.resolve({}) } as Response);
    });
  }

  beforeEach(() => {
    originalFetch = global.fetch;
    sessionStorage.removeItem("tz_checked");
  });

  afterEach(() => {
    global.fetch = originalFetch;
    sessionStorage.removeItem("tz_checked");
  });

  it("auto-detects utcOffset and PUTs when utcOffset is null", async () => {
    setupTzFetch(null);

    render(<Header />);
    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(global.fetch).toHaveBeenCalledWith(
      "/api/user/preferences",
      expect.objectContaining({
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ utcOffset: detectedOffset }),
      })
    );
    expect(sessionStorage.getItem("tz_checked")).toBe("1");
  });

  it("auto-detects utcOffset and PUTs when utcOffset is null", async () => {
    setupTzFetch(null);

    render(<Header />);
    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(global.fetch).toHaveBeenCalledWith(
      "/api/user/preferences",
      expect.objectContaining({
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ utcOffset: detectedOffset }),
      })
    );
  });

  it("does not PUT when utcOffset is already set", async () => {
    setupTzFetch(8);

    render(<Header />);
    await new Promise((resolve) => setTimeout(resolve, 100));

    const putCalls = (global.fetch as ReturnType<typeof vi.fn>).mock.calls.filter(
      (call: [string, RequestInit?]) =>
        call[0] === "/api/user/preferences" && call[1]?.method === "PUT"
    );
    expect(putCalls).toHaveLength(0);
  });

  it("skips detection when sessionStorage tz_checked is set", async () => {
    sessionStorage.setItem("tz_checked", "1");
    setupTzFetch(null);

    render(<Header />);
    await new Promise((resolve) => setTimeout(resolve, 100));

    const putCalls = (global.fetch as ReturnType<typeof vi.fn>).mock.calls.filter(
      (call: [string, RequestInit?]) =>
        call[0] === "/api/user/preferences" && call[1]?.method === "PUT"
    );
    expect(putCalls).toHaveLength(0);
  });
});
