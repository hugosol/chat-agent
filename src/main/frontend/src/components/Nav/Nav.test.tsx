import { describe, it, expect } from "vitest";
import { render, fireEvent } from "@testing-library/react";
import React from "react";
import { Nav } from "./Nav";

function setPath(path: string): void {
  Object.defineProperty(window, "location", {
    value: { pathname: path },
    writable: true,
  });
}

describe("Nav", () => {
  it("renders a logout form with correct action and method", () => {
    const { getByTestId } = render(React.createElement(Nav));
    const form = getByTestId("nav-logout-form") as HTMLFormElement;
    expect(form.getAttribute("action")).toBe("/logout");
    expect(form.method).toBe("post");
    expect(form.querySelector("button")?.textContent).toBe("Logout");
  });

  it("renders a hamburger menu button", () => {
    const { getByTestId } = render(React.createElement(Nav));
    const btn = getByTestId("nav-menu-btn");
    expect(btn.textContent).toBe("\u2630");
    expect(btn.tagName).toBe("BUTTON");
  });

  it("opens sidebar when hamburger button is clicked", () => {
    const { getByTestId } = render(React.createElement(Nav));
    const btn = getByTestId("nav-menu-btn");
    fireEvent.click(btn);
    const sidebar = getByTestId("nav-sidebar");
    expect(sidebar.getAttribute("aria-expanded")).toBe("true");
  });

  it("closes sidebar when close button is clicked", () => {
    const { getByTestId } = render(React.createElement(Nav));
    const menuBtn = getByTestId("nav-menu-btn");
    fireEvent.click(menuBtn);
    const closeBtn = getByTestId("nav-sidebar-close");
    fireEvent.click(closeBtn);
    const sidebar = getByTestId("nav-sidebar");
    expect(sidebar.getAttribute("aria-expanded")).toBe("false");
  });

  it("highlights Chat link on chat page path /", () => {
    setPath("/");
    const { getAllByTestId } = render(React.createElement(Nav));
    const links = getAllByTestId("nav-link");
    const chatLink = links.find((l) => l.textContent?.includes("Chat"));
    expect(chatLink?.getAttribute("data-active")).toBe("true");
  });

  it("highlights Manage link on manage page path", () => {
    setPath("/manage/index.html");
    const { getAllByTestId } = render(React.createElement(Nav));
    const links = getAllByTestId("nav-link");
    const manageLink = links.find((l) => l.textContent?.includes("Manage"));
    expect(manageLink?.getAttribute("data-active")).toBe("true");
  });

  it("does not render token bar on manage page", () => {
    setPath("/manage/index.html");
    const { queryByTestId } = render(React.createElement(Nav));
    expect(queryByTestId("token-bar-fill")).toBeNull();
  });

  it("renders token bar on chat page path /", () => {
    setPath("/");
    const { getByTestId } = render(React.createElement(Nav));
    expect(getByTestId("token-bar-fill")).toBeInTheDocument();
  });

  it("shows green bar when tokenPercent is 0", () => {
    setPath("/");
    const { getByTestId } = render(
      React.createElement(Nav, { tokenPercent: 0 })
    );
    const bar = getByTestId("token-bar-fill");
    expect(bar.style.width).toBe("0%");
    expect(bar.style.backgroundColor).toBe("rgb(39, 174, 96)");
  });

  it("shows yellow bar when tokenPercent is 50", () => {
    setPath("/");
    const { getByTestId } = render(
      React.createElement(Nav, { tokenPercent: 50 })
    );
    const bar = getByTestId("token-bar-fill");
    expect(bar.style.width).toBe("50%");
    expect(bar.style.backgroundColor).toBe("rgb(243, 156, 18)");
  });

  it("shows red bar when tokenPercent is 80", () => {
    setPath("/");
    const { getByTestId } = render(
      React.createElement(Nav, { tokenPercent: 80 })
    );
    const bar = getByTestId("token-bar-fill");
    expect(bar.style.width).toBe("80%");
    expect(bar.style.backgroundColor).toBe("rgb(231, 76, 60)");
  });
});
