"use client";
import { useEffect } from "react";
import { Card } from "../ui/Card";
import { TriangleAlert } from "lucide-react";
import Button from "../ui/Button";
import { useConversion } from "@/context/ConversionContext";

const ErrorStateCard = ({
  error,
  reset,
}: {
  error?: (Error & { digest?: string }) | string;
  reset?: () => void;
}) => {
  useEffect(() => {
    if (error) console.error(error);
  }, [error]);

  const handleRetry = () => {
    reset?.();
    window.location.reload();
  };

  return (
    <div className="w-full h-dvh flex justify-center items-center">
      {/* Error State Card */}
      <Card className="m-2 flex flex-col w-100 h-100">
        <div className="flex items-center justify-center gap-4 rounded-t-xl py-8 bg-red-400 ">
          <TriangleAlert size={100} />
        </div>
        <div className="flex flex-col text-center justify-center p-4 pt-10">
          <h1 className="text-3xl font-bold">Error</h1>
          <p className="text-sm text-muted-foreground text-center">
            An error has occurred. Please try again later.
          </p>
        </div>
        <Button variant="danger" className="self-center" onClick={handleRetry}>
          Retry
        </Button>
      </Card>
    </div>
  );
};

export default ErrorStateCard;

