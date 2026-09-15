"use client";

import MainSection from "@/components/layout/MainSection";
import { useConversion } from "@/context/ConversionContext";

export default function Home() {
    const { resetKey } = useConversion();

    return (
        <main className="min-h-dvh flex flex-col">
            <MainSection key={resetKey} />
        </main>
    );
}
