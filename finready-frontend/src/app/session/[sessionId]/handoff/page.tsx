import { HandoffScreen } from "@/screens/handoff/handoff-screen";

export default async function Page({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}) {
  const { sessionId } = await params;
  return <HandoffScreen sessionId={sessionId} />;
}
