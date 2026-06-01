/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { resolve } from "path";

export default defineConfig({
  define:
    typeof process !== "undefined" && process.env?.NODE_ENV === "test"
      ? {}
      : { "process.env.NODE_ENV": JSON.stringify("production") },
  plugins: [react()],
  build: {
    lib: {
      entry: resolve(__dirname, "src/entry/nav-entry.tsx"),
      name: "ChatAgentNav",
      formats: ["iife"],
      fileName: (format, name) => format === "iife" ? "nav-bundle.js" : `nav-bundle.${format}`,
      cssFileName: "nav-bundle",
    },
    outDir: resolve(__dirname, "../resources/static/shared"),
    emptyOutDir: false,
    rollupOptions: {
      external: ["react", "react-dom"],
      output: {
        globals: {
          react: "React",
          "react-dom": "ReactDOM",
        },
      },
    },
  },
  css: {
    modules: {
      localsConvention: "camelCaseOnly",
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
    css: {
      modules: {
        classNameStrategy: "non-scoped",
      },
    },
  },
});
