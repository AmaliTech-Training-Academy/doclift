import { Card, CardContent, CardDescription, CardHeader } from "@/components/ui/Card";
import type { PreservationItem } from "@/data/preservationData";

interface PreservationCardProps {
    item: PreservationItem;
}

export default function PreservationCard({
    item,
}: PreservationCardProps) {
    const Icon = item.icon;

    return (
        <Card className="group mx-auto w-full transition-all hover:scale-[1.01] hover:shadow-lg">
            <CardHeader>
                <CardDescription className="flex h-fit w-fit rounded-xl bg-blue-100 p-4">
                    <Icon className="size-8 text-blue-600" />
                </CardDescription>
            </CardHeader>

            <CardContent>
                <p className="mb-2 text-xl font-semibold">
                    {item.title}
                </p>

                <p className="mb-2">
                    {item.description}
                </p>

                <div className="flex flex-col rounded-lg bg-blue-100 p-4">
                    <div className="mb-2 flex flex-row justify-between gap-4">
                        <p className="text-sm">
                            {item.comparison.original}
                        </p>

                        <p className="text-sm text-blue-600">
                            {item.comparison.result}
                        </p>
                    </div>

                    <p className="text-sm">
                        {item.comparison.description}
                    </p>
                </div>
            </CardContent>
        </Card>
    );
}