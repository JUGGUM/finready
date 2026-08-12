import { StaffReviewScreen } from "@/screens/s07-staff-review/staff-review-screen";

export default async function Page({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}) {
  const { sessionId } = await params;
  return <StaffReviewScreen sessionId={sessionId} />;
}
