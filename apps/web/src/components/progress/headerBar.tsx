import { Card, CardContent, CardDescription } from "../ui/Card";
import { FileInput, ArrowRight, FileTypeCorner, Timer } from "lucide-react";

const HeaderBar = () => {
  return (
    <div className="mb-8">
      <Card className="mt-10 mx-4 md:w-350 md:mx-auto">
        <CardContent className="flex items-center justify-between gap-4 mt-6">
          {/* Header Bar */}
          <div className="flex flex-col lg:flex-row  gap-6 lg:gap-4 md:items-center md:justify-between w-full">
            {/* Left: Document Pair */}
            <div className="flex flex-col md:flex-row gap-2 sm:gap-4 lg:pr-8">
              {/* Source File */}
              <div className="flex md:items-center md:justify-center gap-2 bg-[#EFF4FF] rounded-lg p-4 sm:p-3">
                <div className="flex items-center justify-center gap-2 bg-red-100 rounded-lg p-4">
                  <FileInput className="size-6 text-red-600" />
                </div>
                <div className="flex flex-col">
                  <h2 className="text-base sm:text-lg font-semibold text-gray-900 mt-2">
                    File Name
                  </h2>
                  <CardDescription className="text-[11px] sm:text-sm text-gray-500">
                    File description or additional information
                  </CardDescription>
                </div>
              </div>
              {/* Arrow Indicator  */}
              <div className="flex flex-col items-center justify-center gap-2 rounded-lg p-2">
                <ArrowRight className="size-5 sm:size-6 text-blue-400 rotate-90" />
              </div>
              {/* Target File */}
              <div className="flex md:items-center md:justify-center gap-2 bg-[#EFF4FF] rounded-lg p-2  sm:p-3">
                <div className="flex items-center justify-center gap-2 bg-blue-200 rounded-lg p-4 ml-2">
                  <FileTypeCorner className="size-6 text-blue-600" />
                </div>
                <div className="flex flex-col">
                  <h2 className="text-base sm:text-lg font-semibold text-gray-900 mt-2">
                    File Name
                  </h2>
                  <CardDescription className="text-[11px] sm:text-sm text-gray-500">
                    File description or additional information
                  </CardDescription>
                </div>
              </div>
            </div>
            {/* Right: Time */}
            <div className="flex items-center justify-center gap-4 lg:ml-8">
              {/* Time elapsed */}
              <div className="flex flex-col items-center justify-center">
                <p className="text-xs sm:text-base text-[#434655] text-center">
                  Time Elapsed
                </p>
                <p className="text-sm sm:text-base">0:14 elapsed</p>
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
                  <p className="text-sm sm:text-base">~0:46s remaining</p>
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
