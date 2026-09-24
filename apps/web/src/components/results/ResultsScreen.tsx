"use client";

import { Download, RotateCw, CircleCheck, Info, ListChecks, ChartLine } from "lucide-react";
import Button from "@/components/ui/Button";
import { ChecklistCard } from "@/components/results/ChecklistCard";
import { FidelityMetricCard } from "@/components/results/FidelityMetricCard";
import { SummaryCard } from "@/components/results/SummaryCard";
import { checklistData, fidelityMetrics, summaryCards } from "@/data/resultsData";
import { useConversion } from "@/context/ConversionContext";


export default function ResultsScreen() {
    const { reset, session } = useConversion();
    const docxTitle = session?.fileName ? session.fileName.replace(/\.[^./]+$/, ".docx") : "Word Document.docx";

    return (
        <div className="flex-1 space-y-4 p-4">
            {/* Header row */}
            <div className="bg-white w-full mx-auto max-w-5xl  flex flex-col space-y-4 p-4 rounded-xl">
                <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                    <div className="flex flex-col gap-2">
                        <div className="flex flex-col text-xs sm:flex-row gap-2 bg-primary-background p-2 rounded-xl sm:items-center">
                            <div className="text-primary flex items-center gap-2">
                                <CircleCheck className="size-3" />
                                <p>Conversion Complete</p>
                            </div>
                            <span className=" hidden sm:block text-primary">•</span>
                            <span className="text-black">Your Word document is ready for download</span>
                        </div>
                        <div className="flex flex-col space-y-1 min-w-0">
                            <h1 className="text-2xl font-regular sm:font-semibold truncate">{docxTitle}</h1>
                            <div className="flex flex-wrap items-center gap-2">
                                <div className="text-xs sm:text-sm bg-primary-background rounded-lg w-fit px-2 py-1 text-primary">
                                    <span>Converted Word Document</span>
                                </div>
                                {session?.jobId && (
                                    <span className="text-xs bg-gray-100 text-gray-600 font-mono px-2 py-1 rounded-md">
                                        ID: {session.jobId}
                                    </span>
                                )}
                                <p className="text-sm sm:text-md text-muted-foreground">
                                    File size • 2 Pages • 20s conversion time
                                </p>
                            </div>
                        </div>
                    </div>
                    <div className="flex flex-col flex-nowrap sm:flex-row gap-2 sm:w-auto w-full shrink-0">
                        <Button variant="secondary" onClick={reset}>
                            <RotateCw className="size-4" />
                            <span className="truncate">Convert Another File</span>
                        </Button>
                        <Button variant="primary">
                            <Download className="size-4" />
                            <span className="truncate">Download Word Document</span>
                        </Button>
                    </div>
                </div>
            </div>

            {/* Checklist + Fidelity two-column section */}
            <div className="w-full mx-auto max-w-5xl grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Left — Structural Conversion Checklist */}
                <div className="flex flex-col space-y-3 bg-white p-4 rounded-xl">
                    <div className="flex flex-row justify-between items-center mb-3">
                        <div className="flex items-center gap-2">
                            <ListChecks className="size-5 text-primary shrink-0" />
                            <h2 className="font-semibold text-2xl">Conversion Checklist</h2>
                        </div>
                        <span className="text-xs text-muted-foreground">Deterministic AST Validation</span>
                    </div>
                    {checklistData.map((item) => (
                        <ChecklistCard key={item.id} item={item} />
                    ))}
                </div>

                {/* Right — Factual Fidelity Metrics */}
                <div className="flex flex-col space-y-3 bg-white p-4 rounded-xl">
                    <div className="flex flex-row justify-between items-center mb-3">
                        <div className="flex items-center gap-2">
                            <ChartLine className="size-5 text-primary shrink-0" />
                            <h2 className="font-semibold text-base">Factual Fidelity Metrics</h2>
                        </div>
                        <span className="text-xs text-muted-foreground">Target: Exact DOCX</span>
                    </div>

                    {/* Deterministic Text Yield donut */}
                    <div className="flex flex-row items-center gap-4 bg-white border border-muted rounded-xl p-4 shadow-sm">
                        <div className="relative flex items-center justify-center w-20 h-20 shrink-0">
                            <svg className="w-20 h-20 -rotate-90" viewBox="0 0 36 36">
                                <circle cx="18" cy="18" r="15.9" fill="none" stroke="var(--muted-foreground)" strokeWidth="3" />
                                <circle
                                    cx="18" cy="18" r="15.9"
                                    fill="none"
                                    stroke="var(--primary)"
                                    strokeWidth="3"
                                    strokeDasharray="98 100"
                                    strokeLinecap="round"
                                />
                            </svg>
                            <div className="absolute flex flex-col items-center leading-none">
                                <span className="text-lg font-bold">98%</span>
                                <span className="text-[9px] text-faded mt-0.5">Text Match</span>
                            </div>
                        </div>
                        <div className="flex flex-col">
                            <p className="font-semibold text-sm">Deterministic Text Yield</p>
                            <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
                                Calculated from Levenshtein token parity between raw PDF content streams and Word runs.
                            </p>
                        </div>
                    </div>

                    {fidelityMetrics.map((item) => (
                        <FidelityMetricCard key={item.id} item={item} />
                    ))}

                    {/* Transparency note */}
                    <div className="flex flex-row items-start gap-3 bg-primary-background border border-primary-foreground rounded-xl p-3 mt-1">
                        <Info className="size-4 text-primary mt-0.5 shrink-0" />
                        <div>
                            <p className="text-xs font-semibold text-primary mb-0.5">DocLift Transparency Principle</p>
                            <p className="text-xs text-primary leading-relaxed">
                                We never fabricate simulated 99.9% metrics. Our parser runs strict geometric audits: when slight manual alignment or cell adjustments are required, we flag the exact page offsets directly in your report.
                            </p>
                        </div>
                    </div>
                </div>
            </div>

            {/* Summary Cards at the bottom section */}
            <div className="w-full mx-auto max-w-5xl grid grid-cols-1 md:grid-cols-3 gap-4">
                {summaryCards.map((item) => (
                    <SummaryCard key={item.id} item={item} />
                ))}
            </div>
        </div>
    );
}
