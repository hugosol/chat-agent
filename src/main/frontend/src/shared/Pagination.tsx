interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

function Pagination({ page, totalPages, onPageChange }: PaginationProps): JSX.Element | null {
  if (totalPages <= 1) return null;

  const pageNums: number[] = [];
  if (totalPages <= 7) {
    for (let i = 0; i < totalPages; i++) pageNums.push(i);
  } else {
    pageNums.push(0, 1, 2);
    pageNums.push(-1);
    for (let i = totalPages - 3; i < totalPages; i++) pageNums.push(i);
  }

  return (
    <div className="pagination" data-testid="pagination">
      <button
        className="page-prev"
        data-testid="page-prev"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
      >
        &lt; 上一页
      </button>
      {pageNums.map((p, i) =>
        p === -1 ? (
          <button key={`ellipsis-${i}`} disabled data-testid="page-ellipsis">
            ...
          </button>
        ) : (
          <button
            key={p}
            className={`page-num${p === page ? " active" : ""}`}
            data-testid="page-num"
            data-active={p === page ? "true" : "false"}
            onClick={() => onPageChange(p)}
          >
            {p + 1}
          </button>
        )
      )}
      <button
        className="page-next"
        data-testid="page-next"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        下一页 &gt;
      </button>
    </div>
  );
}

export { Pagination };
export type { PaginationProps };
