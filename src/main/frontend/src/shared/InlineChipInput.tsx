import { useState, useCallback, useRef, useEffect } from "react";
import type { Tag } from "./types";
import styles from "./InlineChipInput.module.css";

interface InlineChipInputProps {
  options: Tag[];
  value: Tag[];
  onChange: (chips: Tag[]) => void;
  placeholder?: string;
}

function InlineChipInput({
  options,
  value,
  onChange,
  placeholder = "Search tags...",
}: InlineChipInputProps): JSX.Element {
  const [inputValue, setInputValue] = useState("");
  const [showSuggestions, setShowSuggestions] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleRemove = useCallback(
    (index: number) => {
      const next = value.filter((_, i) => i !== index);
      onChange(next);
    },
    [value, onChange]
  );

  const handleAdd = useCallback(
    (tag: Tag) => {
      if (value.some((t) => t.id === tag.id)) return;
      onChange([...value, tag]);
      setInputValue("");
      setShowSuggestions(false);
    },
    [value, onChange]
  );

  const filteredOptions = options.filter(
    (t) =>
      !value.some((v) => v.id === t.id) &&
      t.name.toLowerCase().includes(inputValue.toLowerCase())
  );

  return (
    <div
      ref={containerRef}
      className={styles.container}
      data-testid="inline-chip-input"
      onClick={() => inputRef.current?.focus()}
    >
      {value.map((tag, i) => (
        <span key={tag.id} className={styles.chip} data-testid="inline-chip">
          {tag.name}
          {tag.type === "deck" && " [D]"}
          <span
            className={styles.remove}
            data-testid="inline-chip-remove"
            data-index={i}
            onClick={(e) => {
              e.stopPropagation();
              handleRemove(i);
            }}
          >
            ×
          </span>
        </span>
      ))}
      <input
        ref={inputRef}
        type="text"
        className={styles.input}
        placeholder={placeholder}
        value={inputValue}
        onChange={(e) => {
          setInputValue(e.target.value);
          setShowSuggestions(true);
        }}
        onFocus={() => setShowSuggestions(true)}
        onKeyDown={(e) => {
          if (e.key === "Backspace" && inputValue === "" && value.length > 0) {
            onChange(value.slice(0, -1));
          }
        }}
        data-testid="inline-chip-input-field"
      />
      {showSuggestions && inputValue && filteredOptions.length > 0 && (
        <div className={styles.suggestions} data-testid="inline-chip-suggestions">
          {filteredOptions.map((tag) => (
            <div
              key={tag.id}
              className={styles.suggestion}
              data-testid="inline-chip-suggestion"
              onClick={(e) => {
                e.stopPropagation();
                handleAdd(tag);
              }}
            >
              {tag.name}
              {tag.type === "deck" && " [D]"}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export { InlineChipInput };
export type { InlineChipInputProps };
