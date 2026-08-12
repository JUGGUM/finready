import { ReturnScreen } from "@/screens/handoff/return-screen";

export default async function Page({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}) {
  const { sessionId } = await params;
  return <ReturnScreen sessionId={sessionId} />;
}
