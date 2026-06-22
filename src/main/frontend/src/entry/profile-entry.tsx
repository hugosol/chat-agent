import "../tokens.css";
import React from "react";
import { createRoot } from "react-dom/client";
import { ProfileApp } from "../components/profile/ProfileApp";

function mountProfileApp(): void {
  const root = document.getElementById("root");
  if (root) {
    createRoot(root).render(React.createElement(ProfileApp));
  }
}

const ns = (window as unknown as Record<string, unknown>).ChatAgent || {};
(window as unknown as Record<string, unknown>).ChatAgent = ns;
(ns as Record<string, unknown>).mountProfileApp = mountProfileApp;

mountProfileApp();
