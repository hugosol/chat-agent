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
      entry: resolve(__dirname, "src/entry/settings-entry.tsx"),
      name: "ChatAgent",
      formats: ["iife"],
      fileName: () => "settings-bundle.js",
      cssFileName: "settings-bundle",
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
