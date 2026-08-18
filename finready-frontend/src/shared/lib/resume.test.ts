import { describe, expect, it } from "vitest";
import {
  decideNextAction,
  decideResume,
  RESUME_POINTS,
  resumeHref,
} from "@/shared/lib/resume";

describe("resumeHref", () => {
  it("routes each resume point somewhere real", () => {
    for (const point of RESUME_POINTS) {
      expect(resumeHref("sess_1", point)).toMatch(/^\/session\/sess_1\/[a-z]+$/);
    }
  });

  it("sends the customer steps to the customer route", () => {
    for (const point of ["S04", "S05", "S06"] as const) {
      expect(resumeHref("sess_1", point)).toBe("/session/sess_1/understanding");
    }
  });

  it("sends manual review to the staff screen, not back to the customer", () => {
    expect(resumeHref("sess_1", "S07")).toBe("/session/sess_1/review");
  });

  it("carries the demo scenario through", () => {
    expect(resumeHref("sess_1", "S03", "?scenario=safety")).toBe(
      "/session/sess_1/coverage?scenario=safety",
    );
  });
});

describe("decideResume", () => {
  it("navigates when the server said where to go", () => {
    expect(decideResume("sess_1", "S04")).toEqual({
      kind: "navigate",
      href: "/session/sess_1/understanding",
    });
  });

  it("refuses to guess a destination when the server said nothing", () => {
    // Falling back to the report here would skip risks the customer still
    // has to answer, so "unknown" is the only safe answer.
    for (const missing of [undefined, null]) {
      expect(decideResume("sess_1", missing)).toEqual({ kind: "unknown" });
    }
  });
});

describe("decideNextAction", () => {
  it("routes each server action to a screen", () => {
    expect(decideNextAction("s1", "NEXT_RISK")).toEqual({
      kind: "navigate",
      href: "/session/s1/understanding",
    });
    expect(decideNextAction("s1", "RECHECK")).toEqual({
      kind: "navigate",
      href: "/session/s1/understanding",
    });
    expect(decideNextAction("s1", "STAFF_RESOLUTION_REQUIRED")).toEqual({
      kind: "navigate",
      href: "/session/s1/review",
    });
    expect(decideNextAction("s1", "GO_TO_REPORT")).toEqual({
      kind: "navigate",
      href: "/session/s1/report",
    });
  });

  it("refuses to guess when the server did not say", () => {
    // Falling through to the report here could skip a risk the customer still
    // has to answer, so there is no default.
    expect(decideNextAction("s1", null)).toEqual({ kind: "unknown" });
    expect(decideNextAction("s1", undefined)).toEqual({ kind: "unknown" });
  });
});
