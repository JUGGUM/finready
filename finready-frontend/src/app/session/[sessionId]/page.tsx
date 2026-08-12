import { ResumeScreen } from "@/screens/resume/resume-screen";

export default async function Page({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}) {
  const { sessionId } = await params;
  return <ResumeScreen sessionId={sessionId} />;
}
