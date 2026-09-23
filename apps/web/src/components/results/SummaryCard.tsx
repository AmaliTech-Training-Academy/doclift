import { Card, CardContent, CardHeader, CardDescription } from "@/components/ui/Card";
import { ArrowRight, Check } from "lucide-react";
import type { SummaryCardItem } from "@/data/resultsData";

interface SummaryCardProps {
    item: SummaryCardItem;
}

export function SummaryCard({ item }: SummaryCardProps) {
    const Icon = item.icon;

    return (
        <Card className="group w-full transition-all hover:scale-[1.01] hover:shadow-lg flex flex-col">
            <CardHeader>
                <CardDescription className="flex h-fit w-fit rounded-xl bg-primary-background p-3">
                    <Icon className="size-5 text-primary" />
                </CardDescription>
            </CardHeader>

            <CardContent className="flex flex-col flex-1 gap-3">
                {/* Title + badge */}
                <div className="flex flex-row items-center justify-between gap-2 flex-wrap">
                    <p className="font-semibold text-sm">{item.title}</p>
                    <span className="text-xs bg-gray-100 text-gray-500 rounded-md px-2 py-0.5 whitespace-nowrap">
                        {item.badge}
                    </span>
                </div>

                {/* Description */}
                <p className="text-xs text-muted-foreground leading-relaxed">{item.description}</p>

                {/* ── Variant-specific bottom content ── */}

                {/* figures: file chips */}
                {item.variant === "figures" && item.files && (
                    <div className="flex flex-wrap gap-2 mt-auto pt-2">
                        {item.files.map((f) => (
                            <span
                                key={f.name}
                                className="text-xs bg-gray-100 text-muted-foreground rounded-md px-2 py-1 font-mono"
                            >
                                {f.name}
                            </span>
                        ))}
                        {item.extraFiles && item.extraFiles > 0 && (
                            <span className="text-xs bg-primary-background text-primary rounded-md px-2 py-1">
                                +{item.extraFiles} more
                            </span>
                        )}
                    </div>
                )}

                {/* typography: from → to row */}
                {item.variant === "typography" && item.mapping && (
                    <div className="mt-auto pt-2 flex flex-row items-center justify-between gap-2">
                        <div className="flex items-center gap-2 text-xs text-muted-foreground">
                            <span className="bg-gray-100 rounded-md px-2 py-1 font-medium">
                                {item.mapping.from}
                            </span>
                            <ArrowRight className="size-3 text-gray-400 shrink-0" />
                            <span className="bg-gray-100 rounded-md px-2 py-1 font-medium">
                                {item.mapping.to}
                            </span>
                        </div>
                        <span className="text-xs text-success font-semibold whitespace-nowrap">
                            {item.mapping.matchLabel}
                        </span>
                    </div>
                )}

                {/* session: progress bar */}
                {item.variant === "session" && item.progress !== undefined && (
                    <div className="mt-auto pt-2">
                        <div className="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
                            <div
                                className="h-full bg-primary rounded-full transition-all duration-700"
                                style={{ width: `${item.progress}%` }}
                            />
                        </div>
                    </div>
                )}
            </CardContent>
        </Card>
    );
}
