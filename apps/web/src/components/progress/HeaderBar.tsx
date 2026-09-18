import { Card, CardContent } from "../ui/Card";
import { FileInput, ArrowRight, FileTypeCorner, Timer } from "lucide-react";
import FileInfoCard from "./FileInfoCard";
import { useConversion } from "@/context/ConversionContext";

interface HeaderBarProps {
  file: File | null;
  timeElapsed: number;
  timeRemaining: number;
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatTime(seconds: number): string {
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, "0")}`;
}

const HeaderBar = ({ file, timeElapsed, timeRemaining }: HeaderBarProps) => {
  const sourceFileName = file ? file.name : "Untitled document";
  const targetFileName = file
    ? file.name.replace(/\.[^./]+$/, ".docx")
    : "Untitled document.docx";
  const sourceFileDescription = file
    ? `PDF Document • ${formatFileSize(file.size)}`
    : "No file selected";
  const targetFileDescription = file ? "Word Document (.docx)" : "No file selected";
  const { setActiveView } = useConversion();

  return (
    <div className="mb-8">
      <Card className="mt-10 mx-4 md:w-350 md:mx-auto">
        <CardContent className="flex items-center justify-between gap-4 mt-6">
          {/* Header Bar */}
          <div className="flex flex-col lg:flex-row  gap-6 lg:gap-4 md:items-center md:justify-between w-full">
            {/* Left: Document Pair */}
            <div className="flex flex-col md:flex-row gap-2 sm:gap-4 lg:pr-8">
              {/* Source File */}
              <FileInfoCard
                icon={FileInput}
                iconClassName="text-red-600"
                iconContainerClassName="bg-red-100"
                name={sourceFileName}
                description={sourceFileDescription}
              />
              {/* Arrow Indicator  */}
              <div className="flex flex-col items-center justify-center gap-2 rounded-lg p-2">
                <ArrowRight className="size-5 sm:size-6 text-blue-400 rotate-90 md:rotate-0" />
                <p className="text-xs hidden md:block tracking-widest">DOCX</p>
              </div>
              {/* Target File */}
              <button
                type="button"
                onClick={() => setActiveView("result")}
                className="cursor-pointer rounded-lg transition-opacity hover:opacity-80 text-left"
                aria-label={`Open ${targetFileName}`}
              >
                <FileInfoCard
                  icon={FileTypeCorner}
                  iconClassName="text-blue-600"
                  iconContainerClassName="bg-blue-200"
                  name={targetFileName}
                  description={targetFileDescription}
                />
              </button>
            </div>
            {/* Right: Time */}
            <div className="flex items-center justify-center gap-4 lg:ml-8">
              {/* Time elapsed */}
              <div className="flex flex-col items-center justify-center">
                <p className="text-xs sm:text-base text-[#434655] text-center">
                  Time Elapsed
                </p>
                <p className="text-sm sm:text-base">{formatTime(timeElapsed)} elapsed</p>
              </div>
              {/* Divider */}
              <div className="border-l border-gray-300 h-8 mx-2 sm:mx-4"></div>
              {/* Time remaining */}
              <div className="flex flex-col items-center justify-center">
                <p className="text-xs sm:text-base text-[#434655] text-center">
                  Estimated Time Remaining
                </p>
                <div className="flex gap-2 sm:gap-3 items-center justify-center">
                  <Timer className="size-5 sm:size-6 text-gray-600" />
                  <p className="text-sm sm:text-base">~{formatTime(timeRemaining)}s remaining</p>
                </div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default HeaderBar;
