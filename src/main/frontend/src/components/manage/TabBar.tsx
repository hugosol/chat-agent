import styles from "./TabBar.module.css";

interface TabBarProps {
  activeTab: "cards" | "tags";
  onTabChange: (tab: "cards" | "tags") => void;
}

function TabBar({ activeTab, onTabChange }: TabBarProps): JSX.Element {
  return (
    <nav className={styles.tabs}>
      <button
        className={`${styles.tabBtn}${activeTab === "cards" ? " " + styles.active : ""}`}
        data-testid="tab-cards"
        data-active={activeTab === "cards" ? "true" : "false"}
        onClick={() => onTabChange("cards")}
      >
        Cards
      </button>
      <button
        className={`${styles.tabBtn}${activeTab === "tags" ? " " + styles.active : ""}`}
        data-testid="tab-tags"
        data-active={activeTab === "tags" ? "true" : "false"}
        onClick={() => onTabChange("tags")}
      >
        Tags
      </button>
    </nav>
  );
}

export { TabBar };
export type { TabBarProps };
