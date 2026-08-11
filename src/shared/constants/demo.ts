/**
 * Demo copy shared by the UI and the mock backend.
 *
 * The UI uses these for its "fill sample" affordances; the mock backend
 * uses the same strings to decide what a given answer means. One copy so
 * the two can never disagree.
 *
 * `main` is the representative flow (PRD §18). `safety` is the auxiliary
 * scenario for CONTRADICTED → gate block → override → staff resolution;
 * its exceptions stay out of the main demo.
 */

export type ScenarioId = "main" | "safety";

/* ── transcripts ─────────────────────────────────────────────────── */

const T_INTRO =
  "직원: 오늘 안내드릴 상품은 6개월마다 조건을 평가하는 No-Knock-in Step-down ELS형입니다.\n" +
  "직원: 시장 상황에 따라 투자원금 전액의 손실이 발생할 수 있습니다. 예금이 아니기 때문에 원금이 보장되지 않는 상품입니다.\n" +
  "고객: 원금이 줄어들 수도 있다는 말씀이군요.\n";

const T_R02_CORRECT =
  "직원: 기초자산이 만기 시점에 최초기준가격의 65% 미만이면 하락률만큼 손실이 확정됩니다.\n";

/** The safety scenario's defect: states the opposite of the verified fact. */
const T_R02_CONTRADICTING =
  "직원: 손실 구간이 워낙 낮게 설정되어 있어서, 원금 손실은 사실상 없다고 보시면 됩니다.\n";

const T_R03 =
  "직원: 6개월마다 조기상환 평가일에 기초자산이 기준가격 이상이면 조기상환됩니다. 첫 평가일 기준은 90%입니다.\n";

const T_TAIL =
  "직원: 만기까지 보유하시면 만기평가일 종가 기준으로 상환금액이 결정됩니다.\n" +
  "직원: 중도 환매 시 손실이 있을 수 있습니다. 기초자산 지수 변동이 상환 조건 충족 여부를 결정합니다.\n" +
  "직원: 발행사가 파산하면 원금과 수익 지급이 어려울 수 있습니다. 이 상품은 예금자보호법에 따른 보호 대상이 아닙니다.\n" +
  "직원: 판매수수료와 자산운용 관련 비용이 상환금액에 반영됩니다.\n" +
  "고객: 네, 알겠습니다.";

/** Main demo omits the R03 segment — that is what produces NOT_FOUND. */
export const SAMPLE_TRANSCRIPT: Record<ScenarioId, string> = {
  main: T_INTRO + T_R02_CORRECT + T_TAIL,
  safety: T_INTRO + T_R02_CONTRADICTING + T_R03 + T_TAIL,
};

/** Supplementary explanation offered on the S02 revision pass. */
export const SAMPLE_SUPPLEMENT: Record<ScenarioId, string> = {
  main: "직원: 조기상환 조건을 다시 안내드리겠습니다. 6개월마다 조기상환 평가일에 기초자산이 기준가격 이상이면 조기상환됩니다. 첫 평가일 기준은 90%입니다.",
  safety:
    "직원: 앞서 원금 손실이 사실상 없다고 말씀드린 부분을 정정합니다. 기초자산이 만기 시점에 최초기준가격의 65% 미만이면 하락률만큼 손실이 확정됩니다.",
};

/* ── customer answers ────────────────────────────────────────────── */

export type SampleAnswerKind = "correct" | "wrong" | "vague";

export interface RiskAnswerFixture {
  initial: Record<SampleAnswerKind, string>;
  recheck: Record<SampleAnswerKind, string>;
  /** How the misunderstanding is named back to the customer on S06. */
  misunderstandingSummary: string;
}

export const RISK_ANSWERS: Record<string, RiskAnswerFixture> = {
  R01: {
    initial: {
      correct:
        "예금과 달라서, 만기까지 가져가도 기초자산이 많이 떨어지면 원금이 줄어들 수 있다고 이해했습니다.",
      wrong: "No-KI이고 만기까지 보유하면 원금은 보장되는 것 아닌가요?",
      vague: "네, 알겠습니다.",
    },
    recheck: {
      correct:
        "만기평가일에 기초자산이 최초기준가격의 65% 미만이면 떨어진 비율만큼 원금이 줄어든다고 이해했습니다.",
      wrong: "그래도 만기까지 기다리면 원금은 돌려받는 것 아닌가요?",
      vague: "조건이 안 좋으면 손실이 날 수도 있겠네요.",
    },
    misunderstandingSummary:
      "‘노 녹인(No Knock-in)’ 조건을 원금 보장으로 이해하고 계십니다.",
  },
  R02: {
    initial: {
      correct:
        "만기에 최초기준가격의 65% 미만이면 떨어진 비율만큼 손실이 확정된다고 이해했습니다.",
      wrong: "손실이 나더라도 원금의 10% 정도까지만 손실이 난다고 들었습니다.",
      vague: "많이 떨어지면 손해가 나는 거죠.",
    },
    recheck: {
      correct:
        "떨어진 비율만큼 손실이 확정되므로 절반이 되면 원금도 그만큼 줄어든다고 이해했습니다.",
      wrong: "그래도 손실에 한도가 있는 것으로 알고 있습니다.",
      vague: "글쎄요, 많이 줄어들 것 같습니다.",
    },
    misunderstandingSummary: "손실에 상한이 있다고 이해하고 계십니다.",
  },
  R03: {
    initial: {
      correct:
        "6개월마다 평가일에 기초자산이 그 차수 기준가격 이상이면 조기상환된다고 이해했습니다.",
      wrong: "6개월이 지나면 무조건 조기상환된다고 이해했습니다.",
      vague: "조건이 되면 상환되는 거죠.",
    },
    recheck: {
      correct:
        "조건을 못 채우면 다음 평가일로 넘어가고, 마지막까지 못 채우면 만기 조건으로 상환된다고 이해했습니다.",
      wrong: "조건을 못 채우면 그때 바로 손실이 확정되는 것으로 알고 있습니다.",
      vague: "그건 잘 모르겠습니다.",
    },
    misunderstandingSummary:
      "평가일이 지나면 조건과 무관하게 상환된다고 이해하고 계십니다.",
  },
};
