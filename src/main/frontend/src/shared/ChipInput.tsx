import { useState, useCallback } from "react";
import type { Tag } from "./types";

interface ChipInputProps {
  options: Tag[];
  value: Tag[];
  onChange: (chips: Tag[]) => void;
  placeholder?: string;
}

function ChipInput({
  options,
  value,
  onChange,
  placeholder = "Search tags...",
}: ChipInputProps): JSX.Element {
  const [inputValue, setInputValue] = useState("");

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
    },
    [value, onChange]
  );

  const filteredOptions = options.filter(
    (t) =>
      !value.some((v) => v.id === t.id) &&
      t.name.toLowerCase().includes(inputValue.toLowerCase())
  );

  return (
    <div data-testid="chip-input">
      <div data-testid="chip-list">
        {value.map((tag, i) => (
          <span key={tag.id} className="chip" data-testid="chip">
            {tag.name}
            {tag.type === "deck" && " [D]"}
            <span
              data-testid="chip-remove"
              data-index={i}
              onClick={() => handleRemove(i)}
            >
              ×
            </span>
          </span>
        ))}
      </div>
      <input
        type="text"
        placeholder={placeholder}
        value={inputValue}
        onChange={(e) => setInputValue(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Backspace" && inputValue === "" && value.length > 0) {
            onChange(value.slice(0, -1));
          }
        }}
        data-testid="chip-input-field"
      />
      {inputValue && filteredOptions.length > 0 && (
        <div data-testid="chip-suggestions">
          {filteredOptions.map((tag) => (
            <div
              key={tag.id}
              data-testid="chip-suggestion"
              onClick={() => handleAdd(tag)}
            >
              {tag.name}
              {tag.type === "deck" && " 📁"}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export { ChipInput };
export type { ChipInputProps };
