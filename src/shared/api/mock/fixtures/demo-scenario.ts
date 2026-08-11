import type { SampleAnswerKind } from "@/shared/constants/demo";
import type { UnderstandingAIStatus } from "@/shared/types/domain";

/**
 * Server-side demo fixtures — the part of the scenario the backend owns.
 *
 * The transcript and answer text themselves live in
 * `@/shared/constants/demo` because the UI offers them as samples; only the
 * grading rules are here.
 */

/**
 * Verified sentences the coverage engine locates inside the current
 * revision text. Matching is exact-substring against fixed product data,
 * so nothing written into a transcript can change a verdict.
 */
export interface EvidenceProbe {
  /** Present ⇒ the risk was explained consistently with the fact. */
  supports: string;
  /** Present (without `supports`) ⇒ explained contrary to the fact. */
  contradicts?: string;
  /** Present (without the above) ⇒ mentioned but incomplete. */
  insufficient?: string;
}

export const EVIDENCE_PROBES: Record<string, EvidenceProbe> = {
  R01: { supports: "투자원금 전액의 손실이 발생할 수 있습니다" },
  R02: {
    supports:
      "기초자산이 만기 시점에 최초기준가격의 65% 미만이면 하락률만큼 손실이 확정됩니다",
    contradicts: "원금 손실은 사실상 없다고 보시면 됩니다",
  },
  R03: {
    supports:
      "6개월마다 조기상환 평가일에 기초자산이 기준가격 이상이면 조기상환됩니다. 첫 평가일 기준은 90%입니다",
  },
  R04: {
    supports: "만기까지 보유하시면 만기평가일 종가 기준으로 상환금액이 결정됩니다",
  },
  R05: { supports: "중도 환매 시 손실이 있을 수 있습니다" },
  R06: { supports: "기초자산 지수 변동이 상환 조건 충족 여부를 결정합니다" },
  R07: { supports: "발행사가 파산하면 원금과 수익 지급이 어려울 수 있습니다" },
  R08: { supports: "이 상품은 예금자보호법에 따른 보호 대상이 아닙니다" },
  R09: { supports: "판매수수료와 자산운용 관련 비용이 상환금액에 반영됩니다" },
};

/** Which verdict each designed sample answer is meant to produce. */
export const SAMPLE_ANSWER_STATUS: Record<
  SampleAnswerKind,
  UnderstandingAIStatus
> = {
  correct: "UNDERSTOOD",
  wrong: "MISUNDERSTOOD",
  vague: "UNCERTAIN",
};
