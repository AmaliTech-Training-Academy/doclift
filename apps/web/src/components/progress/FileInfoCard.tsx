import { CardDescription } from "../ui/Card";
import type { LucideIcon } from "lucide-react";

interface FileInfoCardProps {
  icon: LucideIcon;
  iconClassName: string;
  iconContainerClassName: string;
  name?: string;
  description?: string;
}

const FileInfoCard = ({
  icon: Icon,
  iconClassName,
  iconContainerClassName,
  name = "Untitled document",
  description = "No file selected",
}: FileInfoCardProps) => (
  <div className="flex md:items-center md:justify-center gap-2 bg-[#EFF4FF] rounded-lg p-4 md:pr-12">
    <div
      className={`flex items-center justify-center gap-2 rounded-lg p-4 ${iconContainerClassName}`}
    >
      <Icon className={`size-6 ${iconClassName}`} />
    </div>
    <div className="flex flex-col">
      <h2 className="text-base sm:text-lg font-semibold text-gray-900 mt-2">
        {name}
      </h2>
      <CardDescription className="text-[11px] sm:text-sm text-gray-500">
        {description}
      </CardDescription>
    </div>
  </div>
);

export default FileInfoCard;
