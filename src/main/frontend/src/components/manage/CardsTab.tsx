import { useState, useEffect, useCallback } from "react";
import type { Card, Tag, PageResponse } from "../../shared/types";
import { CardToolbar } from "./CardToolbar";
import { CardList } from "./CardList";
import { BatchOperationModal } from "./BatchOperationModal";
import { Modal } from "../../shared/Modal";
import { InlineChipInput } from "../../shared/InlineChipInput";
import { showToast } from "../../shared/Toast";
import { speakText } from "../../shared/tts";
import { formatDate, englishOnly } from "../../shared/utils";

type ModalState =
  | { type: "detail"; card: Card }
  | { type: "create" | "edit"; card?: Card }
  | { type: "confirm-delete"; card: Card }
  | { type: "confirm-forget"; card: Card }
  | { type: "confirm-deck-forget" }
  | { type: "batch"; mode: "import" | "export" }
  | null;

const CARD_STATE_LABELS = ["New", "Learning", "Review", "Relearning"];

function CardsTab(): JSX.Element {
  const [cards, setCards] = useState<Card[]>([]);
  const [decks, setDecks] = useState<Tag[]>([]);
  const [allTags, setAllTags] = useState<Tag[]>([]);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState("front,asc");
  const [deckId, setDeckId] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState<ModalState>(null);

  const [formFront, setFormFront] = useState("");
  const [formBack, setFormBack] = useState("");
  const [formTags, setFormTags] = useState<Tag[]>([]);

  const fetchCards = useCallback(() => {
    let params = `?page=${page}&size=10&sort=${encodeURIComponent(sort)}`;
    if (search) params += `&search=${encodeURIComponent(search)}`;
    if (deckId) params += `&deckId=${encodeURIComponent(deckId)}`;

    fetch(`/api/cards${params}`, { credentials: "same-origin" })
      .then((r) => r.json())
      .then((data: PageResponse<Card>) => {
        setCards(data.content);
        setTotalPages(data.totalPages);
      })
      .catch(() => {
        showToast("加载卡片失败");
      })
      .finally(() => setLoading(false));
  }, [page, sort, search, deckId]);

  const fetchDecks = useCallback(() => {
    fetch("/api/tags?type=deck", { credentials: "same-origin" })
      .then((r) => r.json())
      .then((tags: Tag[]) => setDecks(tags))
      .catch(() => {});
  }, []);

  const fetchAllTags = useCallback(() => {
    fetch("/api/tags", { credentials: "same-origin" })
      .then((r) => r.json())
      .then((tags: Tag[]) => setAllTags(tags))
      .catch(() => {});
  }, []);

  useEffect(() => {
    fetchCards();
    fetchDecks();
  }, [fetchCards, fetchDecks]);

  const handleOpenDetail = useCallback((card: Card) => {
    setModal({ type: "detail", card });
  }, []);

  const handleOpenCreate = useCallback(() => {
    fetchAllTags();
    setFormFront("");
    setFormBack("");
    setFormTags([]);
    setModal({ type: "create" });
  }, [fetchAllTags]);

  const handleOpenEdit = useCallback((card: Card) => {
    fetchAllTags();
    setFormFront(card.front);
    setFormBack(card.back);
    setFormTags(card.tags);
    setModal({ type: "edit", card });
  }, [fetchAllTags]);

  const handleOpenDelete = useCallback((card: Card) => {
    setModal({ type: "confirm-delete", card });
  }, []);

  const handleOpenForget = useCallback((card: Card) => {
    setModal({ type: "confirm-forget", card });
  }, []);

  const handleOpenDeckForget = useCallback(() => {
    setModal({ type: "confirm-deck-forget" });
  }, []);

  const handleCloseModal = useCallback(() => {
    setModal(null);
  }, []);

  const handleCreateSave = useCallback(() => {
    if (!formFront.trim() || !formBack.trim()) {
      showToast("Front and back are required");
      return;
    }
    const tagIds = formTags.map((t) => t.id);
    const front = formFront.trim();
    const back = formBack.trim();
    const payload = JSON.stringify({ front, back, tagIds });

    // Check for conflicts before creating
    fetch("/api/cards/check", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: payload,
      credentials: "same-origin",
    })
      .then((resp) => resp.json())
      .then((checkResult: { conflicts: { tagId: string; tagName: string }[] }) => {
        const conflicts = checkResult.conflicts || [];
        if (conflicts.length === 0) {
          return;
        }
        const sameDeckConflicts = conflicts.filter((c) => tagIds.includes(c.tagId));
        if (sameDeckConflicts.length > 0) {
          const names = sameDeckConflicts.map((c) => c.tagName).join(", ");
          showToast(`卡片 '${front}' 在牌组 '${names}' 中已存在`);
          throw new Error("same-deck-conflict");
        }
        const names = conflicts.map((c) => c.tagName).join(", ");
        if (!window.confirm(`卡片 '${front}' 已在牌组 '${names}' 中存在，确认添加？`)) {
          throw new Error("user-cancelled");
        }
      })
      .then(() => fetch("/api/cards/add", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: payload,
        credentials: "same-origin",
      }))
      .then((resp) => {
        if (resp.ok) {
          setModal(null);
          fetchCards();
          fetchDecks();
          return;
        }
        return resp.text().then((text) => {
          try { return JSON.parse(text); } catch { return { message: text }; }
        }).then((body) => {
          throw { message: body.message, status: resp.status };
        });
      })
      .catch((err: { message?: string; status?: number }) => {
        if (err.status === 422) showToast(err.message ?? "创建失败");
        else if (err.message !== "same-deck-conflict" && err.message !== "user-cancelled") showToast("创建失败");
      });
  }, [formFront, formBack, formTags, fetchCards, fetchDecks]);

  const handleEditSave = useCallback(() => {
    if (!formFront.trim() || !formBack.trim()) {
      showToast("Front and back are required");
      return;
    }
    const cardId = modal?.type === "edit" ? modal.card?.id : undefined;
    if (!cardId) return;
    const tagIds = formTags.map((t) => t.id);
    fetch(`/api/cards/${cardId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ front: formFront.trim(), back: formBack.trim(), tagIds }),
      credentials: "same-origin",
    })
      .then((resp) => {
        if (resp.ok) {
          setModal(null);
          fetchCards();
          return;
        }
        return resp.json().then((body) => {
          throw { message: body.message, status: resp.status };
        });
      })
      .catch((err: any) => {
        if (err.status === 422) showToast(err.message);
        else showToast("保存失败");
      });
  }, [formFront, formBack, formTags, modal, fetchCards]);

  const handleDeleteConfirm = useCallback(() => {
    if (modal?.type !== "confirm-delete") return;
    const cardId = modal.card.id;
    fetch(`/api/cards/${cardId}`, { method: "DELETE", credentials: "same-origin" })
      .then((resp) => {
        if (resp.ok) {
          setModal(null);
          fetchCards();
          return;
        }
        showToast("删除失败");
      })
      .catch(() => showToast("删除失败"));
  }, [modal, fetchCards]);

  const handleForgetConfirm = useCallback(() => {
    if (modal?.type !== "confirm-forget") return;
    const cardId = modal.card.id;
    fetch(`/api/cards/${cardId}/forget`, { method: "POST", credentials: "same-origin" })
      .then((resp) => {
        if (resp.ok) {
          setModal(null);
          fetchCards();
          return;
        }
        showToast("遗忘失败");
      })
      .catch(() => showToast("遗忘失败"));
  }, [modal, fetchCards]);

  const handleDeckForgetConfirm = useCallback(() => {
    if (!deckId) return;
    fetch(`/api/cards/forget?deckId=${encodeURIComponent(deckId)}`, {
      method: "POST",
      credentials: "same-origin",
    })
      .then((resp) => {
        if (resp.ok) {
          setModal(null);
          fetchCards();
          return;
        }
        showToast("重置失败");
      })
      .catch(() => showToast("重置失败"));
  }, [deckId, fetchCards]);

  const handleSpeak = useCallback((text: string) => {
    const eng = englishOnly(text);
    if (eng) speakText(eng);
  }, []);

  const handleBatchOpen = useCallback((mode: "import" | "export") => {
    setModal({ type: "batch", mode });
  }, []);

  const toolbar = (
    <CardToolbar
      search={search}
      sort={sort}
      deckId={deckId}
      decks={decks}
      onSearchChange={(s) => { setSearch(s); setPage(0); }}
      onSortChange={(s) => { setSort(s); setPage(0); }}
      onDeckChange={(id) => { setDeckId(id); setPage(0); }}
      onCreate={handleOpenCreate}
      onBatchOpen={handleBatchOpen}
      onDeckForget={handleOpenDeckForget}
    />
  );

  let content: JSX.Element;
  if (loading) {
    content = <div className="empty-state">加载中...</div>;
  } else if (cards.length === 0) {
    const emptyText = decks.length === 0
      ? "暂无牌组，请先在 Tags 页面创建牌组"
      : "暂无卡片，点击 + 创建";
    content = <div className="empty-state" data-testid="empty-state">{emptyText}</div>;
  } else {
    content = (
      <CardList
        cards={cards}
        page={page}
        totalPages={totalPages}
        onPageChange={setPage}
        onCardClick={handleOpenDetail}
        onCardEdit={handleOpenEdit}
        onCardForget={handleOpenForget}
        onCardDelete={handleOpenDelete}
      />
    );
  }

  return (
    <div>
      {!loading && toolbar}
      {content}

      {modal?.type === "detail" && (
        <Modal open={true} title="Card Detail" onClose={handleCloseModal}>
          <div className="detail-item">
            <div className="detail-label">Front</div>
            <div className="detail-value">
              {modal.card.front}
              {englishOnly(modal.card.front) && (
                <span className="card-tts-btn" onClick={() => handleSpeak(modal.card.front)}>🔊</span>
              )}
            </div>
          </div>
          <div className="detail-item">
            <div className="detail-label">Back</div>
            <div className="detail-value">
              {modal.card.back.split("\n").map((line, i) => (
                <span key={i}>{line}{i < modal.card.back.split("\n").length - 1 && <br />}</span>
              ))}
              {englishOnly(modal.card.back) && (
                <span className="card-tts-btn" onClick={() => handleSpeak(modal.card.back)}>🔊</span>
              )}
            </div>
          </div>
          <div className="detail-item">
            <div className="detail-label">Tags</div>
            <div className="detail-value">
              {modal.card.tags.length > 0
                ? modal.card.tags.map((t) => (
                    <span key={t.id} className="chip">{t.name}{t.type === "deck" ? " [D]" : ""}</span>
                  ))
                : "None"}
            </div>
          </div>
          <div className="detail-item">
            <div className="detail-label">State</div>
            <div className="detail-value">{CARD_STATE_LABELS[modal.card.cardState] ?? modal.card.cardState}</div>
          </div>
          <div className="detail-item">
            <div className="detail-label">Due</div>
            <div className="detail-value">{modal.card.due ? formatDate(modal.card.due) : "-"}</div>
          </div>
          <div className="detail-item">
            <div className="detail-label">Created</div>
            <div className="detail-value">{modal.card.createTime ? formatDate(modal.card.createTime) : "-"}</div>
          </div>
        </Modal>
      )}

      {(modal?.type === "create" || modal?.type === "edit") && (
        <Modal
          open={true}
          title={modal.type === "create" ? "Create Card" : "Edit Card"}
          onClose={handleCloseModal}
          onSave={modal.type === "create" ? handleCreateSave : handleEditSave}
        >
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            <input
              type="text"
              style={{ width: "100%", boxSizing: "border-box" }}
              data-testid="card-form-front"
              placeholder="单词或表达..."
              value={formFront}
              onChange={(e) => setFormFront(e.target.value)}
            />
            <textarea
              style={{
                width: "100%",
                boxSizing: "border-box",
                borderLeft: "2px solid #4fc3f7",
              }}
              data-testid="card-form-back"
              placeholder="释义..."
              rows={4}
              value={formBack}
              onChange={(e) => setFormBack(e.target.value)}
            />
            <InlineChipInput
              options={allTags}
              value={formTags}
              onChange={setFormTags}
              placeholder="搜索标签..."
            />
          </div>
        </Modal>
      )}

      {modal?.type === "confirm-delete" && (
        <Modal
          open={true}
          title="确认删除"
          onClose={handleCloseModal}
          onSave={handleDeleteConfirm}
          saveLabel="Delete"
        >
          <p>确定要删除卡片 "{modal.card.front}" 吗？</p>
        </Modal>
      )}

      {modal?.type === "confirm-forget" && (
        <Modal
          open={true}
          title="遗忘卡片"
          onClose={handleCloseModal}
          onSave={handleForgetConfirm}
          saveLabel="确认遗忘"
          danger={true}
        >
          <p>将删除 {modal.card.reps + modal.card.lapses} 条复习记录，卡片恢复为全新状态。此操作不可撤销。</p>
        </Modal>
      )}

      {modal?.type === "confirm-deck-forget" && (
        <Modal
          open={true}
          title="重置 Deck 全部卡片"
          onClose={handleCloseModal}
          onSave={handleDeckForgetConfirm}
          saveLabel="确认重置"
          danger={true}
        >
          <p>将重置当前牌组中所有卡片为全新状态，并删除所有复习记录。此操作不可撤销。</p>
        </Modal>
      )}

      {modal?.type === "batch" && (
        <BatchOperationModal
          mode={modal.mode}
          onClose={handleCloseModal}
          onComplete={() => {
            fetchCards();
            fetchDecks();
          }}
        />
      )}
    </div>
  );
}

export { CardsTab };
