import "../tokens.css";
import React from "react";
import { createRoot } from "react-dom/client";
import { MoviesApp } from "../components/movies/MoviesApp";

function mountMoviesApp(): void {
  const root = document.getElementById("root");
  if (root) {
    createRoot(root).render(React.createElement(MoviesApp));
  }
}

const ns = (window as unknown as Record<string, unknown>).ChatAgent || {};
(window as unknown as Record<string, unknown>).ChatAgent = ns;
(ns as Record<string, unknown>).mountMoviesApp = mountMoviesApp;

mountMoviesApp();
