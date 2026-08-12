import type { FinReadyApi } from "@/shared/api/contract";
import { MockFinReadyApi } from "@/shared/api/mock/mock-api";
import { SpringFinReadyApi } from "@/shared/api/spring/spring-api";

/**
 * Which adapter the app talks to.
 *
 * `NEXT_PUBLIC_API_MODE=spring` points at the real service; anything else
 * (including unset) uses the in-memory mock, so the UI is developable and
 * demo-able before the backend lands.
 */
const API_MODE = process.env.NEXT_PUBLIC_API_MODE ?? "mock";
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api";

export const api: FinReadyApi =
  API_MODE === "spring"
    ? new SpringFinReadyApi(API_BASE_URL)
    : new MockFinReadyApi();

export const isMockApi = API_MODE !== "spring";

export { FinReadyApiError } from "@/shared/api/contract";
export type { FinReadyApi } from "@/shared/api/contract";
