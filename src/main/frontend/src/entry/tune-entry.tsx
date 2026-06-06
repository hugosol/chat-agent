import React from "react";
import { createRoot } from "react-dom/client";
import { TuneApp } from "../components/tune/TuneApp";

function mountTuneApp(): void {
  const root = document.getElementById("root");
  if (root) {
    createRoot(root).render(React.createElement(TuneApp));
  }
}

const ns = (window as unknown as Record<string, unknown>).ChatAgent || {};
(window as unknown as Record<string, unknown>).ChatAgent = ns;
(ns as Record<string, unknown>).mountTuneApp = mountTuneApp;

mountTuneApp();
