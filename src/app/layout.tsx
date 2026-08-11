import type { Metadata } from "next";
import { Providers } from "@/app/providers";
import "./globals.css";

export const metadata: Metadata = {
  title: "FinReady — 당신이 이해할 때까지, 금융을 더 명확하게.",
  description:
    "금융상품을 설명하는 AI가 아니라, 고객이 핵심 위험을 제대로 이해했는지 확인하는 AI. FinReady는 금융상담을 보조하며 법적 준수 여부를 판정하지 않습니다.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
