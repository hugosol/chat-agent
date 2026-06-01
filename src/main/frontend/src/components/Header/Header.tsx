import { useState, useCallback } from "react";
import classes from "./Header.module.css";

interface HeaderProps {
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

function Header({ tokenPercent }: HeaderProps): JSX.Element {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const openSidebar = useCallback(() => setSidebarOpen(true), []);
  const closeSidebar = useCallback(() => setSidebarOpen(false), []);

  const currentPath = typeof window !== "undefined" ? window.location.pathname : "";
  const isChatPage = currentPath === "/" || currentPath === "/index.html" || currentPath === "";
  const isManagePage = currentPath.startsWith("/manage/");
  const showToken = isChatPage;
  const pct = tokenPercent ?? 0;

  return (
    <div className={classes.navRoot}>
      <form
        action="/logout"
        method="post"
        className={classes.logoutForm}
        data-testid="nav-logout-form"
      >
        <button type="submit" className={classes.btnLogout}>
          Logout
        </button>
      </form>

      {showToken && (
        <div className={classes.tokenBarContainer}>
          <div className={classes.tokenBarLabel}>Token</div>
          <div className={classes.tokenBar}>
            <div
              className={classes.tokenBarFill}
              data-testid="token-bar-fill"
              style={{
                width: pct + "%",
                backgroundColor: tokenColor(pct),
              }}
            />
          </div>
          <div className={classes.tokenBarPct}>{pct}%</div>
        </div>
      )}

      <button
        className={classes.navMenuBtn}
        data-testid="nav-menu-btn"
        onClick={openSidebar}
      >
        {"\u2630"}
      </button>

      <div
        id="navSidebar"
        className={classes.navSidebar}
        data-testid="nav-sidebar"
        aria-expanded={sidebarOpen ? "true" : "false"}
      >
        <div className={classes.navSidebarHeader}>
          <span>Menu</span>
          <button data-testid="nav-sidebar-close" onClick={closeSidebar}>
            {"\u00d7"}
          </button>
        </div>
        <div className={classes.navSidebarLinks}>
          <a
            className={classes.navLink}
            href="/"
            data-testid="nav-link"
            data-active={isChatPage ? "true" : "false"}
          >
            {"\uD83D\uDCAC"} Chat
          </a>
          <a
            className={classes.navLink}
            href="/manage/index.html"
            data-testid="nav-link"
            data-active={isManagePage ? "true" : "false"}
          >
            {"\uD83D\uDCCB"} Manage
          </a>
        </div>
      </div>
    </div>
  );
}

export { Header };
export type { HeaderProps };
