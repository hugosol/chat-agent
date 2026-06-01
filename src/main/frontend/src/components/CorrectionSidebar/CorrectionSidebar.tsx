import { CorrectionData } from "../../shared/types";
import classes from "./CorrectionSidebar.module.css";

interface CorrectionSidebarProps {
  corrections: CorrectionData[];
  collapsed: boolean;
  onToggle: () => void;
}

function CorrectionSidebar({ corrections, collapsed, onToggle }: CorrectionSidebarProps): JSX.Element {
  const expanded = !collapsed;

  return (
    <>
      {corrections.length > 0 && (
        <button
          className={classes.toggleBtn}
          data-testid="correction-toggle"
          onClick={onToggle}
        >
          {"\u26A0\uFE0F"}{" "}
          <span className={classes.badge} data-testid="correction-badge">
            {corrections.length}
          </span>{" "}
          {"\u25C2"}
        </button>
      )}
      <div
        className={classes.sidebar}
        data-testid="correction-sidebar"
        aria-expanded={expanded ? "true" : "false"}
      >
        <div className={classes.header}>
          <button data-testid="correction-sidebar-close" onClick={onToggle}>
            {"\u25B8"}
          </button>
          <span>Corrections ({corrections.length})</span>
        </div>
        <div className={classes.content}>
          {corrections.length === 0 ? (
            <div className={classes.empty} data-testid="correction-sidebar-empty">
              No corrections yet.
            </div>
          ) : (
            corrections.map((c, i) => (
              <div key={i} className={classes.item} data-testid="correction-item">
                <div className={classes.itemType}>{c.type}</div>
                <div className={classes.detail}>
                  <span className={classes.original}>{c.original}</span>
                  <span className={classes.arrow}>{"\u2192"}</span>
                  <span className={classes.corrected}>{c.corrected}</span>
                </div>
                {c.explanation && (
                  <div className={classes.explanation}>{c.explanation}</div>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </>
  );
}

export { CorrectionSidebar };
export type { CorrectionSidebarProps };
