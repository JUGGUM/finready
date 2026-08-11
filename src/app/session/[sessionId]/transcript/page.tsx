import { TranscriptScreen } from "@/screens/s02-transcript/transcript-screen";

export default async function Page({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}) {
  const { sessionId } = await params;
  return <TranscriptScreen sessionId={sessionId} />;
}
