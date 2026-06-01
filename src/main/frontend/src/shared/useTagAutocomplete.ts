import { useState, useEffect, useCallback } from "react";

interface Tag {
  id: number;
  name: string;
  type: string | null;
}

interface UseTagAutocompleteOptions {
  defer?: boolean;
}

function useTagAutocomplete(apiUrl: string, options?: UseTagAutocompleteOptions) {
  const [allTags, setAllTags] = useState<Tag[]>([]);
  const [selected, setSelected] = useState<Tag[]>([]);

  const fetchTags = useCallback(() => {
    fetch(apiUrl, { credentials: "same-origin" })
      .then((r) => r.json())
      .then((tags) => setAllTags(tags as Tag[]))
      .catch(() => {});
  }, [apiUrl]);

  useEffect(() => {
    if (!options?.defer) {
      fetchTags();
    }
  }, [fetchTags, options?.defer]);

  return {
    options: allTags,
    selected,
    setSelected,
    fetchTags,
  };
}

export { useTagAutocomplete };
export type { Tag };
