"use client";

import { FinReadyApiError } from "@/shared/api";

/**
 * One calm, consistent failure surface.
 *
 * It states what failed and offers a retry when the error says retrying is
 * meaningful — no stack traces, no alarm colours beyond the warm block
 * palette already used for "needs attention".
 */
export function ErrorNote({
  error,
  onRetry,
  className = "",
}: {
  error: unknown;
  onRetry?: () => void;
  className?: string;
}) {
  const apiError = error instanceof FinReadyApiError ? error : null;
  const message =
    apiError?.message ??
    (error instanceof Error
      ? error.message
      : "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.");
  // `recoverable` is the contract's own signal; only it earns a retry button.
  const canRetry = onRetry && (apiError ? apiError.recoverable : true);

  return (
    <div
      role="alert"
      className={`flex items-start gap-[14px] rounded-[12px] border border-[var(--color-block-line)] bg-[var(--color-block-bg)] px-[24px] py-[18px] ${className}`}
    >
      <span
        aria-hidden
        className="mt-[2px] size-[20px] flex-none rounded-full bg-[var(--color-block-icon)] text-center text-[13px]/[20px] font-bold text-white"
      >
        !
      </span>
      <div className="flex-1">
        <p className="text-[15.5px] leading-[1.5] font-semibold">{message}</p>
        {apiError ? (
          <p className="mt-[6px] font-mono text-[11.5px] text-[var(--color-block-body)]">
            {apiError.code}
            {apiError.requestId ? ` · ${apiError.requestId}` : ""}
          </p>
        ) : null}
      </div>
      {canRetry ? (
        <button
          type="button"
          onClick={onRetry}
          className="flex-none rounded-[9px] border border-[var(--color-override-line)] bg-white px-[16px] py-[9px] text-[14px] font-semibold hover:bg-[oklch(0.97_0.004_85)]"
        >
          다시 시도
        </button>
      ) : null}
    </div>
  );
}
