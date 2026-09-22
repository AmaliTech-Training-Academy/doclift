"use client";

import { useEffect } from "react";
import UploadScreen from "@/components/upload/UploadScreen";
import ProgressScreen from "@/components/progress/ProgressScreen";
import ResultsScreen from "@/components/results/ResultsScreen";
import { useConversion } from "@/context/ConversionContext";
import { getConversionSession } from "@/lib/conversionSession";

export default function Home() {
    const { resetKey, activeView, setActiveView, setSession } = useConversion();

    useEffect(() => {
        const session = getConversionSession();

        if (!session) {
            setActiveView("upload");
            return;
        }

        setSession(session);

        if (session.status === "converting" || session.status === "uploading") {
            setActiveView("progress");
        } else if (session.status === "completed") {
            setActiveView("result");
        } else if (session.status === "failed" || session.status === "expired") {
            setSession(null);
            setActiveView("upload");
        }
    }, [setActiveView, setSession]);

    return (
        <main className="min-h-dvh flex flex-col">
            {activeView === "upload" && <UploadScreen key={resetKey} />}
            {activeView === "progress" && <ProgressScreen />}
            {activeView === "result" && <ResultsScreen />}
        </main>
    );
}
