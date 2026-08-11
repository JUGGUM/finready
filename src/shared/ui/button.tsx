import type { ButtonHTMLAttributes } from "react";

/**
 * The three button roles the design uses, at the two sizes it uses them.
 * Sizes carry the reference's half-pixel type scale on purpose.
 */

type Variant = "primary" | "secondary" | "quiet";
type Size = "md" | "sm";

const VARIANT: Record<Variant, string> = {
  primary:
    "bg-[var(--color-accent)] text-white font-semibold hover:bg-[var(--color-accent-hover)] disabled:bg-[var(--color-accent-disabled)] disabled:hover:bg-[var(--color-accent-disabled)]",
  secondary:
    "border border-[oklch(0.86_0.008_260)] bg-white font-semibold hover:bg-[oklch(0.96_0.004_85)] disabled:opacity-60",
  quiet:
    "text-[var(--color-muted-soft)] underline underline-offset-[3px] hover:text-[var(--color-ink-body)] disabled:opacity-60 disabled:hover:text-[var(--color-muted-soft)]",
};

const SIZE: Record<Variant, Record<Size, string>> = {
  primary: {
    md: "text-[15.5px] px-[28px] py-[14px] rounded-[10px]",
    sm: "text-[14.5px] px-[20px] py-[11px] rounded-[9px]",
  },
  secondary: {
    md: "text-[15.5px] px-[22px] py-[14px] rounded-[10px]",
    sm: "text-[14px] px-[18px] py-[11px] rounded-[9px]",
  },
  quiet: {
    md: "text-[13.5px] px-[2px] py-[6px]",
    sm: "text-[12.5px] px-[2px] py-[6px]",
  },
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

export function Button({
  variant = "primary",
  size = "md",
  className = "",
  type = "button",
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`${VARIANT[variant]} ${SIZE[variant][size]} ${className}`}
      {...props}
    />
  );
}
