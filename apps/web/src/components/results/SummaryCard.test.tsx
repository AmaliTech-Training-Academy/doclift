import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { SummaryCard } from "./SummaryCard";
import { Image as ImageIcon, Type, ShieldCheck } from "lucide-react";
import type { SummaryCardItem } from "@/data/resultsData";

describe("SummaryCard", () => {
    it("renders common content: title, badge, description, and icon", () => {
        const item: SummaryCardItem = {
            id: 1,
            title: "Base Summary",
            description: "Base description text.",
            icon: ShieldCheck,
            badge: "Active",
            variant: "session",
        };

        const { container } = render(<SummaryCard item={item} />);

        expect(screen.getByText("Base Summary")).toBeInTheDocument();
        expect(screen.getByText("Active")).toBeInTheDocument();
        expect(screen.getByText("Base description text.")).toBeInTheDocument();
        expect(container.querySelector("svg.lucide-shield-check")).toBeInTheDocument();
    });

    it("renders figures variant with file list and extra files indicator", () => {
        const item: SummaryCardItem = {
            id: 2,
            title: "Extracted Figures",
            description: "PNG & vector assets separated.",
            icon: ImageIcon,
            badge: "4 Assets",
            variant: "figures",
            files: [{ name: "fig_01.png" }, { name: "chart_02.png" }],
            extraFiles: 2,
        };

        render(<SummaryCard item={item} />);

        expect(screen.getByText("fig_01.png")).toBeInTheDocument();
        expect(screen.getByText("chart_02.png")).toBeInTheDocument();
        expect(screen.getByText("+2 more")).toBeInTheDocument();
    });

    it("renders typography variant with mapping details and match label", () => {
        const item: SummaryCardItem = {
            id: 3,
            title: "Font Mapping",
            description: "Embedded fonts matched.",
            icon: Type,
            badge: "100% Match",
            variant: "typography",
            mapping: {
                from: "HelveticaNeue-Bold",
                to: "Aptos Display",
                matchLabel: "Exact Metric",
            },
        };

        render(<SummaryCard item={item} />);

        expect(screen.getByText("HelveticaNeue-Bold")).toBeInTheDocument();
        expect(screen.getByText("Aptos Display")).toBeInTheDocument();
        expect(screen.getByText("Exact Metric")).toBeInTheDocument();
    });

    it("renders session variant with progress bar width percentage", () => {
        const item: SummaryCardItem = {
            id: 4,
            title: "Session Storage",
            description: "Storage allocation.",
            icon: ShieldCheck,
            badge: "4.2 MB / 50 MB",
            variant: "session",
            progress: 65,
        };

        const { container } = render(<SummaryCard item={item} />);

        const progressBar = container.querySelector('div[style*="width: 65%"]');
        expect(progressBar).toBeInTheDocument();
    });
});
