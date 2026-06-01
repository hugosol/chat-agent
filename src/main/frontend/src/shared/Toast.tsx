import { useEffect } from "react";
import { createRoot } from "react-dom/client";

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
    <div className="flashcard-toast show" data-testid="toast">
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
