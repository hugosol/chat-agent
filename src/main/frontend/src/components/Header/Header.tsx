import { useState, useEffect, useCallback } from "react";
import classes from "./Header.module.css";

type PanelType = "menu" | "correction" | "debug" | "flashcard" | null;

interface HeaderProps {
  tokenPercent?: number;
  activePanel?: PanelType;
  onTogglePanel?: (panel: PanelType) => void;
}

const GREEN = "rgb(39, 174, 96)";
const YELLOW = "rgb(243, 156, 18)";
const RED = "rgb(231, 76, 60)";

function tokenColor(pct: number): string {
  if (pct >= 80) return RED;
  if (pct >= 50) return YELLOW;
  return GREEN;
}

function Header({ tokenPercent, activePanel, onTogglePanel }: HeaderProps): JSX.Element {
  const [localSidebarOpen, setLocalSidebarOpen] = useState(false);
  const [username, setUsername] = useState<string | null>(null);

  useEffect(() => {
    fetch("/api/user/me")
      .then((r) => r.json())
      .then((data) => setUsername(data.username))
      .catch(() => setUsername("Logout"));
  }, []);

  useEffect(() => {
    if (sessionStorage.getItem("tz_checked")) return;
    fetch("/api/user/preferences")
      .then((r) => r.json())
      .then((prefs) => {
        if (prefs.utcOffset != null) return;
        const offset = -(new Date().getTimezoneOffset() / 60);
        fetch("/api/user/preferences", {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ utcOffset: offset }),
        });
      })
      .catch(() => {})
      .finally(() => sessionStorage.setItem("tz_checked", "1"));
  }, []);

  const sidebarOpen = activePanel !== undefined ? activePanel === "menu" : localSidebarOpen;

  const openSidebar = useCallback(() => {
    if (onTogglePanel) {
      onTogglePanel("menu");
    } else {
      setLocalSidebarOpen(true);
    }
  }, [onTogglePanel]);

  const closeSidebar = useCallback(() => {
    if (onTogglePanel) {
      onTogglePanel("menu");
    } else {
      setLocalSidebarOpen(false);
    }
  }, [onTogglePanel]);

  const currentPath = typeof window !== "undefined" ? window.location.pathname : "";
  const isChatPage = currentPath === "/" || currentPath === "/index.html" || currentPath === "";
  const isManagePage = currentPath.startsWith("/manage/");
  const isReviewPage = currentPath.startsWith("/review/");
  const isTunePage = currentPath.startsWith("/tune/");
  const isMoviesPage = currentPath.startsWith("/movies/");
  const isSettingsPage = currentPath.startsWith("/settings/");
  const isProfilePage = currentPath.startsWith("/profile/");
  const showToken = isChatPage;
  const pct = tokenPercent ?? 0;

  const displayName = username ?? "...";

  return (
    <div className={classes.navRoot}>
      <div className={classes.userArea} data-testid="nav-user-btn">
        <span className={classes.userLabel}>{"\u25C1"} {displayName}</span>
        <form
          action="/logout"
          method="post"
          className={classes.logoutForm}
        >
          <button type="submit" className={classes.btnLogout} aria-label="Logout"></button>
        </form>
      </div>

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
          <a
            className={classes.navLink}
            href="/review/index.html"
            data-testid="nav-link"
            data-active={isReviewPage ? "true" : "false"}
          >
            {"\uD83D\uDCDD"} Review
          </a>
          <a
            className={classes.navLink}
            href="/tune/index.html"
            data-testid="nav-tune-link"
            data-active={isTunePage ? "true" : "false"}
          >
            {"\uD83D\uDCD0"} Tune
          </a>
          <a
            className={classes.navLink}
            href="/movies/index.html"
            data-testid="nav-movies-link"
            data-active={isMoviesPage ? "true" : "false"}
          >
            {"\uD83C\uDFAC"} Movies
          </a>
          <a
            className={classes.navLink}
            href="/settings/index.html"
            data-testid="nav-settings"
            data-active={isSettingsPage ? "true" : "false"}
          >
            {"\u2699"} 设置
          </a>
          <a
            className={classes.navLink}
            href="/profile/index.html"
            data-testid="nav-profile-link"
            data-active={isProfilePage ? "true" : "false"}
          >
            {"\uD83D\uDC64"} Profile
          </a>
        </div>
      </div>
    </div>
  );
}

export { Header };
export type { HeaderProps };
