import "../tokens.css";
import React from "react";
import { createRoot } from "react-dom/client";
import { SettingsApp } from "../components/settings/SettingsApp";

function mountSettingsApp(): void {
  const root = document.getElementById("root");
  if (root) {
    createRoot(root).render(React.createElement(SettingsApp));
  }
}

const ns = (window as unknown as Record<string, unknown>).ChatAgent || {};
(window as unknown as Record<string, unknown>).ChatAgent = ns;
(ns as Record<string, unknown>).mountSettingsApp = mountSettingsApp;

mountSettingsApp();
