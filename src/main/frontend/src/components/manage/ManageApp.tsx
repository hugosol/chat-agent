import { useState } from "react";
import { Header } from "../Header/Header";
import { TabBar } from "./TabBar";
import { CardsTab } from "./CardsTab";
import { TagsTab } from "./TagsTab";
import styles from "./ManageApp.module.css";

function ManageApp(): JSX.Element {
  const [activeTab, setActiveTab] = useState<"cards" | "tags">("cards");

  return (
    <div className={styles.app}>
      <Header />
      <div className={`manage-layout ${styles.layout}`}>
        <div className={styles.content}>
          {activeTab === "cards" ? <CardsTab /> : <TagsTab />}
        </div>
        <TabBar activeTab={activeTab} onTabChange={setActiveTab} />
      </div>
    </div>
  );
}

export { ManageApp };
