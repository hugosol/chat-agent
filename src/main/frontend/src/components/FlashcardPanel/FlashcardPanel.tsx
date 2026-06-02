import React, { useState, useEffect, useCallback } from "react";
import styles from "./FlashcardPanel.module.css";

interface Tag {
  id: number;
  name: string;
  type: string | null;
}

interface FlashcardPanelProps {
  isOpen: boolean;
  onToggle: () => void;
}

type Stage = 1 | 2;

export function FlashcardPanel({ isOpen, onToggle }: FlashcardPanelProps): React.ReactElement {
  const [stage, setStage] = useState<Stage>(1);
  const [front, setFront] = useState("");
  const [back, setBack] = useState("");
  const [chips, setChips] = useState<Tag[]>([]);
  const [tagInput, setTagInput] = useState("");
  const [allTags, setAllTags] = useState<Tag[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [toast, setToast] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const filteredTags = allTags.filter(
    (t) =>
      !chips.some((c) => c.id === t.id) &&
      t.name.toLowerCase().includes(tagInput.toLowerCase())
  );

  const fetchTags = useCallback(() => {
    fetch("/api/tags", { credentials: "same-origin" })
      .then((r) => r.json())
      .then((tags) => setAllTags(tags as Tag[]))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (isOpen) {
      setStage(1);
      setFront("");
      setBack("");
      setChips([]);
      setTagInput("");
      setShowSuggestions(false);
      setError("");
      fetchTags();
    }
  }, [isOpen, fetchTags]);

  function handleContinue(): void {
    if (!front.trim()) return;
    setStage(2);
  }

  function addChip(tag: Tag): void {
    if (chips.some((c) => c.id === tag.id)) return;
    setChips([...chips, tag]);
    setTagInput("");
    setShowSuggestions(false);
  }

  function removeChip(index: number): void {
    setChips(chips.filter((_, i) => i !== index));
  }

  async function handleSave(): Promise<void> {
    if (!front.trim() || !back.trim()) return;
    setSaving(true);
    setError("");
    try {
      const res = await fetch("/api/cards/add", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          front: front.trim(),
          back: back.trim(),
          tagIds: chips.map((t) => t.id),
        }),
        credentials: "same-origin",
      });
      if (res.ok) {
        setToast(true);
        setTimeout(() => {
          setToast(false);
          onToggle();
        }, 2500);
      } else if (res.status === 422) {
        const data = await res.json();
        setError(data.message || "Validation error");
      } else {
        setError("Save failed");
      }
    } catch {
      setError("Network error");
    } finally {
      setSaving(false);
    }
  }

  return React.createElement(
    React.Fragment,
    null,
    React.createElement(
      "button",
      {
        "data-testid": "flashcard-toggle",
        className: styles.toggleBtn,
        onClick: onToggle,
      },
      "anki"
    ),
    React.createElement(
      "div",
      {
        "data-testid": "flashcard-panel",
        className: styles.panel,
        "aria-expanded": isOpen ? "true" : "false",
      },
      isOpen &&
        React.createElement(
          React.Fragment,
          null,
          React.createElement(
            "div",
            { className: styles.header },
            React.createElement("span", { className: styles.title }, "anki"),
            React.createElement(
              "button",
              { className: styles.closeBtn, onClick: onToggle },
              "\u00d7"
            )
          ),
          stage === 1 &&
            React.createElement(
              "div",
              { className: styles.stage, "data-testid": "flashcard-stage1" },
              React.createElement("input", {
                "data-testid": "flashcard-front",
                type: "text",
                className: styles.frontInput,
                placeholder: "Front (word)",
                value: front,
                onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
                  setFront(e.target.value),
                onKeyDown: (e: React.KeyboardEvent) => {
                  if (e.key === "Enter") handleContinue();
                },
              }),
              React.createElement(
                "button",
                {
                  "data-testid": "flashcard-continue",
                  className: `${styles.btn} ${styles.btnPrimary} ${styles.continueBtn}`,
                  onClick: handleContinue,
                  disabled: !front.trim(),
                },
                "Continue"
              )
            ),
          stage === 2 &&
            React.createElement(
              "div",
              { "data-testid": "flashcard-stage2", className: styles.stage2 },
              React.createElement("textarea", {
                "data-testid": "flashcard-back",
                className: styles.textarea,
                placeholder: "Back (translation/meaning)",
                value: back,
                onChange: (e: React.ChangeEvent<HTMLTextAreaElement>) =>
                  setBack(e.target.value),
              }),
              React.createElement(
                "div",
                { className: styles.tagArea },
                React.createElement(
                  "div",
                  { className: styles.chips },
                  ...chips.map((chip, i) =>
                    React.createElement(
                      "span",
                      {
                        key: chip.id,
                        "data-testid": "flashcard-chip",
                        className: styles.chip,
                      },
                      chip.name,
                      React.createElement(
                        "span",
                        {
                          className: styles.chipRemove,
                          onClick: () => removeChip(i),
                        },
                        "\u00d7"
                      )
                    )
                  )
                ),
                React.createElement("input", {
                  "data-testid": "flashcard-tag-input",
                  type: "text",
                  className: styles.tagInput,
                  placeholder: "Add tag...",
                  value: tagInput,
                  onChange: (e: React.ChangeEvent<HTMLInputElement>) => {
                    setTagInput(e.target.value);
                    if (!showSuggestions) setShowSuggestions(true);
                  },
                  onFocus: () => {
                    if (!allTags.length) fetchTags();
                    setShowSuggestions(true);
                  },
                  onKeyDown: (e: React.KeyboardEvent) => {
                    if (e.key === "Backspace" && !tagInput && chips.length > 0) {
                      removeChip(chips.length - 1);
                    }
                  },
                }),
                showSuggestions &&
                  React.createElement(
                    "div",
                    {
                      "data-testid": "flashcard-tag-suggestions",
                      className: styles.suggestions,
                    },
                    ...filteredTags.map((tag) =>
                      React.createElement(
                        "div",
                        {
                          key: tag.id,
                          "data-testid": "tag-suggestion-item",
                          className: styles.suggestionItem,
                          onClick: () => addChip(tag),
                        },
                        tag.name
                      )
                    )
                  )
              ),
              error &&
                React.createElement(
                  "div",
                  { className: styles.error },
                  error
                ),
              React.createElement(
                "button",
                {
                  "data-testid": "flashcard-save",
                  className: `${styles.btn} ${styles.btnPrimary} ${styles.saveBtn}`,
                  onClick: handleSave,
                  disabled: saving || !front.trim() || !back.trim(),
                },
                saving ? "Saving..." : "Save"
              )
            )
        )
    ),
    toast &&
      React.createElement(
        "div",
        {
          "data-testid": "flashcard-toast",
          className: styles.toast,
        },
        "Saved"
      )
  );
}
