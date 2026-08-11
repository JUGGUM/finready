"use client";

/**
 * The customer → staff hand-back.
 *
 * The mirror of the outbound hand-off: the customer's part is over, and
 * the device goes back before anything is decided. Nothing is concluded on
 * this screen — the staff member reviews the result and closes the session.
 */
export function ReturnScreen() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-[var(--color-cust-canvas)] px-[40px]">
      <div className="screen-in w-full max-w-[560px] text-center">
        <div className="mx-auto flex size-[44px] items-center justify-center rounded-full bg-[var(--color-ok-icon)]">
          <span className="text-[18px] font-bold text-white">✓</span>
        </div>

        <h1 className="mt-[32px] text-[32px] leading-[1.35] font-semibold tracking-[-0.022em] text-pretty">
          고객 확인이 끝났습니다
        </h1>
        <p className="mt-[18px] text-[17px] leading-[1.7] text-[var(--color-cust-body)]">
          화면을 담당 직원에게 전달해주세요. 확인 결과는 담당 직원이 검토한 뒤
          상담을 종료합니다.
        </p>

        <p className="mt-[36px] text-[13px] leading-[1.7] text-[var(--color-cust-muted-soft)]">
          FinReady는 금융상담을 보조하며 법적 준수 여부를 판정하지 않습니다.
        </p>
      </div>
    </main>
  );
}
