import { useEffect } from "react";
import { createRoot } from "react-dom/client";
import styles from "./Toast.module.css";

interface ToastProps {
  message: string;
  duration?: number;
  onClose: () => void;
}

function Toast({ message, duration = 2100, onClose }: ToastProps): JSX.Element {
  useEffect(() => {
    const timer = setTimeout(onClose, duration);
    return () => clearTimeout(timer);
  }, [duration, onClose]);

  return (
    <div className={styles.toast} data-testid="toast">
      {message}
    </div>
  );
}

function showToast(message: string, duration = 2100): void {
  const div = document.createElement("div");
  document.body.appendChild(div);
  const root = createRoot(div);
  root.render(
    <Toast
      message={message}
      duration={duration}
      onClose={() => {
        root.unmount();
        div.remove();
      }}
    />
  );
}

export { Toast, showToast };
export type { ToastProps };
