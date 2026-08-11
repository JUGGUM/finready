import { ReportScreen } from "@/screens/s08-report/report-screen";

export default async function Page({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}) {
  const { sessionId } = await params;
  return <ReportScreen sessionId={sessionId} />;
}
