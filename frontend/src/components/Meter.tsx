/** Một tỉ lệ phần trăm dạng thanh ngang, nhãn và số ghi ngay trên thanh. */
export function Meter({ label, value, detail }: { label: string; value: number; detail?: string }) {
  return (
    <div>
      <div className="flex items-baseline justify-between gap-3 text-sm">
        <span className="font-bold">{label}</span>
        <span>
          <span className="font-black">{value}%</span>
          {detail && <span className="font-semibold text-muted-foreground"> ({detail})</span>}
        </span>
      </div>
      <div
        className="mt-1.5 h-3 overflow-hidden rounded-full bg-secondary-soft"
        role="meter"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={value}
        aria-label={label}
      >
        {/* Cùng màu xanh đã kiểm tra bằng validator bảng màu ở trang Tiến bộ. */}
        <div className="h-full rounded-full bg-secondary-dark" style={{ width: `${value}%` }} />
      </div>
    </div>
  )
}
