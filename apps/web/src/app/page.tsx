"use client";

import UploadScreen from "@/components/upload/UploadScreen";
import ResultsScreen from "@/components/results/ResultsScreen";
import { useConversion } from "@/context/ConversionContext";

export default function Home() {
    const { resetKey } = useConversion();

    return (
        <main className="min-h-dvh flex flex-col">
            <UploadScreen key={resetKey} />
            <ResultsScreen />
        </main>
    );
}
