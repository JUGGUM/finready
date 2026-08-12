import { CoverageScreen } from "@/screens/s03-coverage/coverage-screen";

export default async function Page({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}) {
  const { sessionId } = await params;
  return <CoverageScreen sessionId={sessionId} />;
}
