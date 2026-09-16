"use client";


import { useConversion } from "@/context/ConversionContext";
import MainSection from "@/components/layout/MainSection";

export default function Home() {
    const { resetKey } = useConversion();

    return (
        <main className="min-h-dvh flex flex-col">
            <MainSection key={resetKey} />
        </main>
    );
}
