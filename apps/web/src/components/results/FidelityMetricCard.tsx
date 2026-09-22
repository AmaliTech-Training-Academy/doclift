import { Card, CardContent } from "@/components/ui/Card";
import type { FidelityMetric } from "@/data/resultsData";

interface FidelityMetricCardProps {
    item: FidelityMetric;
}

export function FidelityMetricCard({ item }: FidelityMetricCardProps) {
    const Icon = item.icon;

    return (
        <Card className={`w-full transition-all hover:scale-[1.01] hover:shadow-md ${item.highlight ? "border-yellow-300 bg-yellow-50" : ""}`}>
            <CardContent className="flex flex-row items-center justify-between gap-4 p-4">
                <div className="flex flex-row items-center gap-3">
                    <Icon className="size-4 text-gray-500 shrink-0" />
                    <p className="text-sm text-gray-700">{item.label}</p>
                </div>
                <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold">{item.value}</span>
                    {item.note && (
                        <span className={`text-sm rounded-md px-2 py-0.5 ${item.highlight ? "bg-yellow-200 text-yellow-800" : "text-gray-400"}`}>
                            {item.note}
                        </span>
                    )}
                </div>
            </CardContent>
        </Card>
    );
}
