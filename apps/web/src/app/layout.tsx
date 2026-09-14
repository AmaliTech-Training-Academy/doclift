import type { Metadata } from "next";
import { DM_Serif_Display, Inter_Tight } from "next/font/google";
import "./globals.css";

import Header from "../components/layout/header";
import Footer from "../components/layout/footer";
import { cn } from "@/lib/utils"

const dmSerif = DM_Serif_Display({
  variable: "--font-heading",
  subsets: ["latin"],
  weight: '400',
});

const inter = Inter_Tight({
  variable: "--font-body",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: `DocLift`,
  description: "Convert your PDF files to Word documents",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={cn("h-full", "antialiased", dmSerif.variable, "font-sans", inter.variable)}
    >
      <body className="min-h-full flex flex-col">
        <Header />
          <main>
            {children}
          </main>
        <Footer />
      </body>
    </html>
  );
}
