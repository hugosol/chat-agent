import { createRoot } from "react-dom/client";
import { Header } from "../components/Header/Header";

function mountHeader(container: HTMLElement, props?: { tokenPercent?: number }): void {
  createRoot(container).render(<Header {...props} />);
}

const ns = (window as any).ChatAgent || {};
(window as any).ChatAgent = ns;
ns.mountHeader = mountHeader;
