import React, { useState, useCallback } from "react";
import classes from "./Nav.module.css";

interface NavProps {
  tokenPercent?: number;
}

const GREEN = "rgb(39, 174, 96)";
const YELLOW = "rgb(243, 156, 18)";
const RED = "rgb(231, 76, 60)";

function tokenColor(pct: number): string {
  if (pct >= 80) return RED;
  if (pct >= 50) return YELLOW;
  return GREEN;
}

function Nav({ tokenPercent }: NavProps): React.ReactElement {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const openSidebar = useCallback(() => setSidebarOpen(true), []);
  const closeSidebar = useCallback(() => setSidebarOpen(false), []);

  const currentPath = typeof window !== "undefined" ? window.location.pathname : "";
  const isChatPage = currentPath === "/" || currentPath === "/index.html" || currentPath === "";
  const isManagePage = currentPath.startsWith("/manage/");
  const showToken = isChatPage;
  const pct = tokenPercent ?? 0;

  return React.createElement(
    "div",
    { className: classes.navRoot },
    React.createElement(
      "form",
      {
        action: "/logout",
        method: "post",
        className: classes.logoutForm,
        "data-testid": "nav-logout-form",
      },
      React.createElement(
        "button",
        { type: "submit", className: classes.btnLogout },
        "Logout"
      )
    ),
    showToken &&
      React.createElement(
        "div",
        { className: classes.tokenBarContainer },
        React.createElement("div", { className: classes.tokenBarLabel }, "Token"),
        React.createElement(
          "div",
          { className: classes.tokenBar },
          React.createElement("div", {
            className: classes.tokenBarFill,
            "data-testid": "token-bar-fill",
            style: {
              width: pct + "%",
              backgroundColor: tokenColor(pct),
            },
          })
        ),
        React.createElement(
          "div",
          { className: classes.tokenBarPct },
          pct + "%"
        )
      ),
    React.createElement(
      "button",
      {
        className: classes.navMenuBtn,
        "data-testid": "nav-menu-btn",
        onClick: openSidebar,
      },
      "\u2630"
    ),
    React.createElement(
      "div",
      {
        id: "navSidebar",
        className: classes.navSidebar,
        "data-testid": "nav-sidebar",
        "aria-expanded": sidebarOpen ? "true" : "false",
      },
      React.createElement("div", { className: classes.navSidebarHeader },
        React.createElement("span", null, "Menu"),
        React.createElement(
          "button",
          { "data-testid": "nav-sidebar-close", onClick: closeSidebar },
          "\u00d7"
        )
      ),
      React.createElement("div", { className: classes.navSidebarLinks },
        React.createElement("a", {
          className: classes.navLink,
          href: "/",
          "data-testid": "nav-link",
          "data-active": isChatPage ? "true" : "false",
        }, "\uD83D\uDCAC Chat"),
        React.createElement("a", {
          className: classes.navLink,
          href: "/manage/index.html",
          "data-testid": "nav-link",
          "data-active": isManagePage ? "true" : "false",
        }, "\uD83D\uDCCB Manage")
      )
    )
  );
}

export { Nav };
export type { NavProps };
