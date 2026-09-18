import { Card, CardContent, CardHeader, CardDescription } from "@/components/ui/Card";
import { ArrowRight, Check } from "lucide-react";
import type { SummaryCardItem } from "@/data/resultsData";

interface SummaryCardProps {
    item: SummaryCardItem;
}

export function SummaryCard({ item }: SummaryCardProps) {
    const Icon = item.icon;

    return (
        <Card className="group h-full flex flex-col justify-between border border-gray-200/90 bg-white hover:border-blue-300 hover:shadow-lg hover:shadow-blue-500/5 hover:-translate-y-0.5 transition-all duration-300 rounded-xl overflow-hidden">
            <CardHeader className="p-5 pb-3 space-y-3">
                <div className="flex items-center justify-between">
                    <div className="size-11 rounded-xl bg-blue-50 border border-blue-100/80 flex items-center justify-center text-blue-600 group-hover:bg-blue-600 group-hover:text-white group-hover:scale-105 transition-all duration-300 shadow-2xs">
                        <Icon className="size-5" />
                    </div>
                    {item.badge && (
                        <span className="text-[14px] font-semibold tracking-wide px-2.5 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-100/80 group-hover:bg-blue-600 group-hover:text-white group-hover:border-blue-600 transition-all duration-300">
                            {item.badge}
                        </span>
                    )}
                </div>

                <div>
                    <h3 className="text-xl font-semibold text-gray-900 group-hover:text-blue-600 transition-colors">
                        {item.title}
                    </h3>
                    <CardDescription className="text-sm sm:text-md text-gray-500 leading-relaxed mt-1">
                        {item.description}
                    </CardDescription>
                </div>
            </CardHeader>

            <CardContent className="p-5 pt-0 mt-auto">
                <div className="rounded-xl bg-slate-50/90 border border-slate-200/70 p-3.5 space-y-2.5 transition-colors group-hover:border-blue-100 group-hover:bg-blue-50/20">
                    {/* figures variant: assets chips */}
                    {item.variant === "figures" && item.files && (
                        <div>
                            <div className="flex items-center justify-between mb-2">
                                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                                    Embedded Assets
                                </span>
                                <span className="text-sm font-semibold text-blue-700">
                                    Lossless PNG
                                </span>
                            </div>
                            <div className="flex flex-wrap gap-1.5">
                                {item.files.map((f) => (
                                    <span
                                        key={f.name}
                                        className="text-sm bg-white border border-slate-200/80 text-slate-700 rounded-md px-2 py-1 font-mono shadow-2xs"
                                    >
                                        {f.name}
                                    </span>
                                ))}
                                {item.extraFiles && item.extraFiles > 0 && (
                                    <span className="text-sm bg-blue-50 border border-blue-100/80 text-blue-700 font-medium rounded-md px-2 py-1">
                                        +{item.extraFiles} more
                                    </span>
                                )}
                            </div>
                        </div>
                    )}

                    {/* typography variant: from → to mapping */}
                    {item.variant === "typography" && item.mapping && (
                        <div>
                            <div className="flex items-center justify-between gap-2">
                                <div className="flex flex-col min-w-0">
                                    <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                                        Source Font
                                    </span>
                                    <span className="text-sm font-medium text-slate-700">
                                        {item.mapping.from}
                                    </span>
                                </div>
                                <div className="flex items-center justify-center size-6 rounded-full bg-blue-50 text-blue-600 border border-blue-100/80 shrink-0">
                                    <ArrowRight className="size-3" />
                                </div>
                                <div className="flex flex-col items-end text-right min-w-0">
                                    <span className="text-[10px] font-bold uppercase tracking-wider text-blue-600">
                                        Target Style
                                    </span>
                                    <span className="text-sm font-semibold text-blue-700">
                                        {item.mapping.to}
                                    </span>
                                </div>
                            </div>

                            {item.mapping.matchLabel && (
                                <div className="pt-2 mt-2 border-t border-slate-200/60 flex items-start gap-1.5 text-sm text-slate-500">
                                    <Check className="size-3.5 text-emerald-500 shrink-0 mt-0.5" />
                                    <span className="leading-snug">{item.mapping.matchLabel}</span>
                                </div>
                            )}
                        </div>
                    )}

                    {/* session variant: auto-purge progress indicator */}
                    {item.variant === "session" && item.progress !== undefined && (
                        <div className="space-y-2">
                            <div className="flex items-center justify-between gap-2">
                                <div className="flex flex-col min-w-0">
                                    <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                                        Cache Status
                                    </span>
                                    <span className="text-sm font-medium text-slate-700">
                                        Auto-purges in 60m
                                    </span>
                                </div>
                                <div className="flex flex-col items-end text-right min-w-0">
                                    <span className="text-[10px] font-bold uppercase tracking-wider text-emerald-600">
                                        Privacy Active
                                    </span>
                                    <span className="text-sm font-semibold text-slate-700">
                                        {item.badge}
                                    </span>
                                </div>
                            </div>

                            <div className="pt-1">
                                <div className="w-full h-2 bg-slate-200/70 rounded-full overflow-hidden">
                                    <div
                                        className="h-full bg-blue-600 rounded-full transition-all duration-700"
                                        style={{ width: `${item.progress}%` }}
                                    />
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            </CardContent>
        </Card>
    );
}
