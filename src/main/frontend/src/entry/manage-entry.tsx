import React from "react";
import { createRoot } from "react-dom/client";
import { ManageApp } from "../components/manage/ManageApp";

function mountManageApp(): void {
  const root = document.getElementById("root");
  if (root) {
    createRoot(root).render(React.createElement(ManageApp));
  }
}

const ns = (window as unknown as Record<string, unknown>).ChatAgent || {};
(window as unknown as Record<string, unknown>).ChatAgent = ns;
(ns as Record<string, unknown>).mountManageApp = mountManageApp;

mountManageApp();
