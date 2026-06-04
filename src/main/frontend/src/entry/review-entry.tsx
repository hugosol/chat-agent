import React from "react";
import { createRoot } from "react-dom/client";
import { ReviewApp } from "../components/review/ReviewApp";

function mountReviewApp(): void {
  const root = document.getElementById("review-root");
  if (root) {
    createRoot(root).render(React.createElement(ReviewApp));
  }
}

const ns = (window as unknown as Record<string, unknown>).ChatAgent || {};
(window as unknown as Record<string, unknown>).ChatAgent = ns;
(ns as Record<string, unknown>).mountReviewApp = mountReviewApp;

mountReviewApp();
