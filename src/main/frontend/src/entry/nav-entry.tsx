import { createRoot } from "react-dom/client";
import { Nav } from "../components/Nav/Nav";

function mount(container: HTMLElement, props?: { tokenPercent?: number }): void {
  createRoot(container).render(<Nav {...props} />);
}

(window as any).ChatAgentNav = { mount };
