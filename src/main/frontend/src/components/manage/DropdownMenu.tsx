import { useState, useRef, useEffect, useCallback } from "react";
import styles from "./DropdownMenu.module.css";

interface DropdownItem {
  label: string;
  value: string;
  onClick: (value: string) => void;
}

interface DropdownMenuProps {
  label: string;
  items: DropdownItem[];
  selectedValue: string;
  testId?: string;
  optionTestId?: string;
}

function DropdownMenu({ label, items, selectedValue, testId, optionTestId }: DropdownMenuProps): JSX.Element {
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const handleClickOutside = useCallback((e: MouseEvent) => {
    if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
      setOpen(false);
    }
  }, []);

  useEffect(() => {
    if (open) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [open, handleClickOutside]);

  const selectedItem = items.find((i) => i.value === selectedValue);

  return (
    <div className={styles.container} ref={menuRef}>
      <button
        className={styles.trigger}
        data-testid={testId}
        onClick={() => setOpen(!open)}
        aria-expanded={open}
      >
        {selectedItem?.label ?? label}
      </button>
      {open && (
        <div className={styles.menu}>
          {items.map((item) => (
            <button
              key={item.value}
              className={`${styles.item}${item.value === selectedValue ? " " + styles.selected : ""}`}
              data-testid={optionTestId}
              onClick={() => {
                item.onClick(item.value);
                setOpen(false);
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export { DropdownMenu };
export type { DropdownMenuProps, DropdownItem };
