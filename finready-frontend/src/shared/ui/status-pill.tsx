import {
  COVERAGE_STATUS_LABEL,
  UNDERSTANDING_AI_STATUS_LABEL,
} from "@/shared/constants/labels";
import type {
  CoverageStatus,
  UnderstandingAIStatus,
} from "@/shared/types/domain";

/**
 * Status marks for coverage and understanding.
 *
 * Each state gets its own *shape* as well as its own colour — filled,
 * half, dashed ring, triangle — so the four coverage states stay
 * distinguishable without relying on hue alone.
 */

type DotShape = "filled" | "half" | "dashed" | "ring" | "triangle";

interface StatusStyle {
  label: string;
  bg: string;
  fg: string;
  dotColor: string;
  shape: DotShape;
}

const COVERAGE_STYLE: Record<CoverageStatus, StatusStyle> = {
  EXPLAINED: {
    label: COVERAGE_STATUS_LABEL.EXPLAINED,
    bg: "var(--color-ok-bg)",
    fg: "var(--color-ok-fg)",
    dotColor: "var(--color-ok-dot)",
    shape: "filled",
  },
  INSUFFICIENT: {
    label: COVERAGE_STATUS_LABEL.INSUFFICIENT,
    bg: "var(--color-warn-bg)",
    fg: "var(--color-warn-fg)",
    dotColor: "var(--color-warn-dot)",
    shape: "half",
  },
  NOT_FOUND: {
    label: COVERAGE_STATUS_LABEL.NOT_FOUND,
    bg: "var(--color-none-bg)",
    fg: "var(--color-none-fg)",
    dotColor: "var(--color-none-dot)",
    shape: "dashed",
  },
  CONTRADICTED: {
    label: COVERAGE_STATUS_LABEL.CONTRADICTED,
    bg: "var(--color-bad-bg)",
    fg: "var(--color-bad-fg)",
    dotColor: "var(--color-bad-dot)",
    shape: "triangle",
  },
};

const UNDERSTANDING_STYLE: Record<UnderstandingAIStatus, StatusStyle> = {
  UNDERSTOOD: {
    label: UNDERSTANDING_AI_STATUS_LABEL.UNDERSTOOD,
    bg: "var(--color-ok-bg)",
    fg: "var(--color-ok-fg)",
    dotColor: "var(--color-ok-dot)",
    shape: "filled",
  },
  MISUNDERSTOOD: {
    label: UNDERSTANDING_AI_STATUS_LABEL.MISUNDERSTOOD,
    bg: "var(--color-bad-u-bg)",
    fg: "var(--color-bad-u-fg)",
    dotColor: "var(--color-bad-dot)",
    shape: "triangle",
  },
  UNCERTAIN: {
    label: UNDERSTANDING_AI_STATUS_LABEL.UNCERTAIN,
    bg: "var(--color-warn-bg)",
    fg: "var(--color-warn-fg)",
    dotColor: "var(--color-warn-dot)",
    shape: "ring",
  },
};

export function StatusDot({
  shape,
  color,
  size = 9,
}: {
  shape: DotShape;
  color: string;
  size?: number;
}) {
  if (shape === "triangle") {
    const half = size / 2 + 0.5;
    return (
      <span
        aria-hidden
        style={{
          flex: "none",
          width: 0,
          height: 0,
          borderLeft: `${half}px solid transparent`,
          borderRight: `${half}px solid transparent`,
          borderBottom: `${size}px solid ${color}`,
        }}
      />
    );
  }

  const base: React.CSSProperties = {
    flex: "none",
    width: size,
    height: size,
    borderRadius: "50%",
    boxSizing: "border-box",
  };

  if (shape === "dashed") {
    return <span aria-hidden style={{ ...base, border: `2px dashed ${color}` }} />;
  }
  if (shape === "ring") {
    return <span aria-hidden style={{ ...base, border: `2px solid ${color}` }} />;
  }
  if (shape === "half") {
    return (
      <span
        aria-hidden
        style={{
          ...base,
          background: `linear-gradient(90deg, ${color} 50%, var(--color-warn-dot-trail) 50%)`,
        }}
      />
    );
  }
  return <span aria-hidden style={{ ...base, background: color }} />;
}

function Pill({ style, className }: { style: StatusStyle; className?: string }) {
  return (
    <span
      className={`inline-flex items-center gap-[7px] rounded-full px-[11px] py-[5px] text-[13px] font-semibold ${className ?? ""}`}
      style={{ background: style.bg, color: style.fg }}
    >
      <StatusDot shape={style.shape} color={style.dotColor} />
      {style.label}
    </span>
  );
}

export function CoverageStatusPill({
  status,
  className,
}: {
  status: CoverageStatus;
  className?: string;
}) {
  return <Pill style={COVERAGE_STYLE[status]} className={className} />;
}

export function UnderstandingStatusPill({
  status,
  className,
}: {
  status: UnderstandingAIStatus;
  className?: string;
}) {
  return <Pill style={UNDERSTANDING_STYLE[status]} className={className} />;
}

export function coverageStyle(status: CoverageStatus): StatusStyle {
  return COVERAGE_STYLE[status];
}

export function understandingStyle(status: UnderstandingAIStatus): StatusStyle {
  return UNDERSTANDING_STYLE[status];
}
