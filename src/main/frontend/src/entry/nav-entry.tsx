import React from "react";
import { createRoot } from "react-dom/client";
import { Nav } from "../components/Nav/Nav";

function mount(container: HTMLElement, props?: { tokenPercent?: number }): void {
  createRoot(container).render(React.createElement(Nav, props));
}

(window as any).ChatAgentNav = { mount };
