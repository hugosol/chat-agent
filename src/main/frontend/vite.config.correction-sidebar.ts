/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { resolve } from "path";

export default defineConfig({
  define: { "process.env.NODE_ENV": JSON.stringify("production") },
  plugins: [react()],
  build: {
    lib: {
      entry: resolve(__dirname, "src/entry/correction-sidebar-entry.tsx"),
      name: "ChatAgent",
      formats: ["iife"],
      fileName: () => "correction-sidebar-bundle.js",
      cssFileName: "correction-sidebar-bundle",
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
});
