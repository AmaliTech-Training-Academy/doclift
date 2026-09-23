"use client";

import UploadScreen from "@/components/upload/UploadScreen";
import ProgressScreen from "@/components/progress/ProgressScreen";
import ResultsScreen from "@/components/results/ResultsScreen";
import { useConversion } from "@/context/ConversionContext";

export default function Home() {
  const { resetKey, activeView } = useConversion();

  const { reset } = useConversion();

  return (
  
    <main className="min-h-dvh flex flex-col">
      {activeView === "upload" && <UploadScreen key={resetKey} />}
      {activeView === "progress" && <ProgressScreen />}
      {activeView === "result" && <ResultsScreen />}
    </main>
  
  );
}
