import type { ReactNode } from "react";

interface ModalProps {
  open: boolean;
  title: string;
  children: ReactNode;
  onClose: () => void;
  onSave?: () => void;
}

function Modal({ open, title, children, onClose, onSave }: ModalProps): JSX.Element | null {
  if (!open) return null;

  return (
    <div className="modal" data-testid="modal-overlay" onClick={() => onClose()}>
      <div className="modal-content" data-testid="modal-content" onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
        <div className="modal-body">{children}</div>
        <div className="modal-actions">
          <button className="btn btn-cancel" data-testid="modal-cancel" onClick={onClose}>
            Cancel
          </button>
          {onSave && (
            <button className="btn btn-primary btn-save" data-testid="modal-save" onClick={onSave}>
              Save
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export { Modal };
export type { ModalProps };
