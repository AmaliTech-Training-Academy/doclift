import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
} from "@/components/ui/Card";
import type { PreservationItem } from "@/data/preservationData";
import { ArrowRight, Check } from "lucide-react";

interface PreservationCardProps {
    item: PreservationItem;
}

export default function PreservationCard({ item }: PreservationCardProps) {
    const Icon = item.icon;

    return (
        <Card className="group h-full flex flex-col justify-between border border-gray-200/90 bg-white hover:border-blue-300 hover:shadow-lg hover:shadow-blue-500/5 hover:-translate-y-0.5 transition-all duration-300 rounded-xl overflow-hidden">
            <CardHeader className="p-5 pb-3 space-y-3">
                <div className="flex items-center justify-between">
                    <div className="size-11 rounded-xl bg-blue-50 border border-blue-100/80 flex items-center justify-center text-blue-600 group-hover:bg-blue-600 group-hover:text-white group-hover:scale-105 transition-all duration-300 shadow-2xs">
                        <Icon className="size-5" />
                    </div>
                    {item.category && (
                        <span className="text-[11px] font-semibold tracking-wide px-2.5 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-100/80 group-hover:bg-blue-600 group-hover:text-white group-hover:border-blue-600 transition-all duration-300">
                            {item.category}
                        </span>
                    )}
                </div>

                <div>
                    <h3 className="text-base sm:text-lg font-semibold text-gray-900 group-hover:text-blue-600 transition-colors">
                        {item.title}
                    </h3>
                    <CardDescription className="text-xs sm:text-sm text-gray-500 leading-relaxed mt-1">
                        {item.description}
                    </CardDescription>
                </div>
            </CardHeader>

            <CardContent className="p-5 pt-0 mt-auto">
                <div className="rounded-xl bg-slate-50/90 border border-slate-200/70 p-3.5 space-y-2.5 transition-colors group-hover:border-blue-100 group-hover:bg-blue-50/20">
                    <div className="flex items-center justify-between gap-2">
                        <div className="flex flex-col min-w-0">
                            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                                PDF Input
                            </span>
                            <span className="text-xs font-medium text-slate-700">
                                {item.comparison.original}
                            </span>
                        </div>
                        <div className="flex items-center justify-center size-6 rounded-full bg-blue-50 text-blue-600 border border-blue-100/80 shrink-0">
                            <ArrowRight className="size-3" />
                        </div>
                        <div className="flex flex-col items-end text-right min-w-0">
                            <span className="text-[10px] font-bold uppercase tracking-wider text-blue-600">
                                DOCX Output
                            </span>
                            <span className="text-xs font-semibold text-blue-700">
                                {item.comparison.result}
                            </span>
                        </div>
                    </div>

                    {item.comparison.description && (
                        <div className="pt-2 border-t border-slate-200/60 flex items-start gap-1.5 text-xs text-slate-500">
                            <Check className="size-3.5 text-emerald-500 shrink-0 mt-0.5" />
                            <span className="leading-snug">{item.comparison.description}</span>
                        </div>
                    )}
                </div>
            </CardContent>
        </Card>
    );
}
