import { PrepareScreen } from "@/screens/s01-prepare/prepare-screen";

export default async function Page({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}) {
  const { sessionId } = await params;
  return <PrepareScreen sessionId={sessionId} />;
}
