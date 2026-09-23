import React from "react";
import { Card } from "../ui/Card";
import { TriangleAlert } from "lucide-react";
import Button from "../ui/Button";
import { useConversion } from "@/context/ConversionContext";

const ErrorStateCard = () => {
  const { reset } = useConversion();
  return (
    <div className="w-full h-dvh flex justify-center items-center">
      {/* Error State Card */}
      <Card className="m-2 flex flex-col w-100 h-100">
        <div className="flex items-center justify-center gap-4 rounded-t-xl py-8 bg-red-400 ">
          <TriangleAlert size={100} />
        </div>
        <div className="flex flex-col text-center justify-center p-4 pt-10">
          <h1 className="text-3xl font-bold">Error</h1>
          <p>An error has occurred. Your File conversion failed.</p>
          <p className="text-sm text-gray-600 text-center">
            Please try again later.
          </p>
        </div>
        <Button variant="danger" className="self-center" onClick={reset}>
          Retry
        </Button>
      </Card>
    </div>
  );
};

export default ErrorStateCard;
