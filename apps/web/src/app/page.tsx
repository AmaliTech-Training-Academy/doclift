import type { Metadata } from "next";
import MainSection from "@/components/layout/MainSection";


export const metadata: Metadata = {
  title: "DocLift - Convert",
};

export default function Home() {
    return (
        <main>
            <MainSection/>
        </main>
    );
}
