import { createRoot } from "react-dom/client";
import { CorrectionSidebar } from "../components/CorrectionSidebar/CorrectionSidebar";
import type { CorrectionData } from "../shared/types";

interface CorrectionSidebarAPI {
  addCorrection(c: CorrectionData): void;
  clear(): void;
  getCount(): number;
}

function mountCorrectionSidebar(container: HTMLElement): CorrectionSidebarAPI {
  let corrections: CorrectionData[] = [];
  let collapsed = true;

  const root = createRoot(container);

  function render(): void {
    root.render(
      <CorrectionSidebar
        corrections={corrections}
        collapsed={collapsed}
        onToggle={() => {
          collapsed = !collapsed;
          render();
        }}
      />
    );
  }

  render();

  return {
    addCorrection(c: CorrectionData) {
      corrections = [...corrections, c];
      render();
    },
    clear() {
      corrections = [];
      collapsed = true;
      render();
    },
    getCount() {
      return corrections.length;
    },
  };
}

const ns = (window as any).ChatAgent || {};
(window as any).ChatAgent = ns;
ns.mountCorrectionSidebar = mountCorrectionSidebar;
