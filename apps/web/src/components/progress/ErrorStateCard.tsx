import { Card } from "../ui/Card";
import { TriangleAlert } from "lucide-react";
import Button from "../ui/Button";
import { useConversion } from "@/context/ConversionContext";

export interface ErrorStateCardProps {
  error?: string;
  reset?: () => void;
}

const ErrorStateCard = ({ error, reset }: ErrorStateCardProps = {}) => {
  const { resetKeepFile } = useConversion();
  const handleRetry = reset ?? resetKeepFile;
  const errorMessage = error ?? "An error has occurred. Your file conversion failed.";

  return (
    <div className="w-full py-12 flex justify-center items-center">
      {/* Error State Card */}
      <Card className="m-2 flex flex-col w-full max-w-md overflow-hidden shadow-lg border-red-100">
        <div className="flex items-center justify-center gap-4 rounded-t-xl py-8 bg-red-500 text-white">
          <TriangleAlert size={72} />
        </div>
        <div className="flex flex-col text-center justify-center p-6 space-y-2">
          <h1 className="text-3xl font-bold text-foreground">Error</h1>
          <p className="text-base text-foreground font-medium">{errorMessage}</p>
          <p className="text-sm text-muted-foreground">
            Click retry to try the conversion once more.
          </p>
        </div>
        <div className="p-4 pt-0 flex justify-center">
          <Button variant="danger" className="min-w-36" onClick={handleRetry}>
            Retry
          </Button>
        </div>
      </Card>
    </div>
  );
};

export default ErrorStateCard;
