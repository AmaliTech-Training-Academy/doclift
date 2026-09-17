import {
    FileText,
    Grid3x2,
    List,
    Bold,
    Image as ImageIcon,
    type LucideIcon,
} from "lucide-react";

export interface PreservationItem {
    id: number;
    title: string;
    description: string;
    icon: LucideIcon;
    comparison: {
        original: string;
        result: string;
        description: string;
    };
}

export const preservationData: PreservationItem[] = [
    {
        id: 1,
        title: "Reading Order",
        description: "Rebuilds multi-column & asymmetric column flows naturally.",
        icon: FileText,
        comparison: {
            original: "Column Flow",
            result: "Contiguous Flow",
            description:
                "Rebuilds multi-column & asymmetric column flows naturally.",
        },
    },
    {
        id: 2,
        title: "Headings & Paragraphs",
        description:
            "Maps typography into native Word heading styles (H1–H4).",
        icon: FileText,
        comparison: {
            original: "Original Text",
            result: "Semantic Heading Styles",
            description:
                "Maps all heading styles into native Word heading styles.",
        },
    },
    {
        id: 3,
        title: "Editable Data Tables",
        description:
            "Converted to native Word tables with structured rows, columns, and borders.",
        icon: Grid3x2,
        comparison: {
            original: "Original Table",
            result: "Word Table",
            description:
                "Converted to native Word tables with structured rows, columns, and borders.",
        },
    },
    {
        id: 4,
        title: "Bulleted & Numbered Lists",
        description:
            "Recreates true hierarchical lists, not raw bullet characters.",
        icon: List,
        comparison: {
            original: "List Engine",
            result: "Bulleting style Managed",
            description:
                "Tier 1 Operational Node • Nested verification child",
        },
    },
    {
        id: 5,
        title: "Bold, Italic & Underline",
        description:
            "Character formatting and inline spans fully retained.",
        icon: Bold,
        comparison: {
            original: "Inline Spans",
            result: "100% Retained",
            description:
                "Standard text with bold weight and slanted emphasis",
        },
    },
    {
        id: 6,
        title: "Embedded Images",
        description:
            "Preserved at native resolution in their exact reading context.",
        icon: ImageIcon,
        comparison: {
            original: "Raster & Vectors",
            result: "Lossless Wrap",
            description:
                "Lossless Extraction • 300 DPI preserved",
        },
    },
];