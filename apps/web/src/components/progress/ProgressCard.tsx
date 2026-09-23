import { useCallback, useRef, useState } from "react";
import { toast } from "sonner";
import { Card } from "../ui/Card";
import { Progress } from "../ui/Progress";
import { Spinner } from "../ui/Spinner";
import { CheckIcon, BadgeCheck, X } from "lucide-react";
import {
  VerticalStepperDemo,
  type VerticalStepperDemoHandle,
  type PipelineProgress,
} from "../ui/Stepper";
import Button from "../ui/Button";
import ErrorStateCard from "./ErrorStateCard";

import { useConversion } from "@/context/ConversionContext";

const ProgressCard = () => {
  const { updateStatus, session } = useConversion();
  const stepperRef = useRef<VerticalStepperDemoHandle>(null);
  const [cancelled, setCancelled] = useState(false);
  const [progress, setProgress] = useState<PipelineProgress | null>(null);
  const done = progress?.done ?? false;

  const isFailed = session?.status === "failed" || Boolean(progress?.error);

  const handleCancel = () => {
    if (cancelled) return;
    stepperRef.current?.cancel();
    setCancelled(true);
    updateStatus("failed");
    toast.error("Conversion cancelled");
  };

  const handleProgress = useCallback((state: PipelineProgress) => {
    setProgress(state);
    if (state.error || state.cancelled) {
      updateStatus("failed");
    } else if (state.done) {
      updateStatus("done");
    }
  }, [updateStatus]);

  const overallPercent = progress?.overallPercent ?? 0;

  const steps = [
    {
      title: "Step 1",
      description: "Initializing document processing...",
    },
    {
      title: "Step 2",
      description: "Analyzing document structure...",
    },
    {
      title: "Step 3",
      description:
        "Reconstructing tabular data structures and nested headers...",
    },
    {
      title: "Step 4",
      description: "Validating and cleaning the reconstructed data...",
    },
    {
      title: "Step 5",
      description: "Finalizing the document reconstruction process...",
    },
  ];

  const totalSteps = progress?.totalSteps ?? steps.length;
  const currentStepIndex = Math.min(progress?.activeIndex ?? 0, totalSteps - 1);
  const isFinalPhase = currentStepIndex + 1 === totalSteps;

  if (isFailed) {
    return <ErrorStateCard />;
  }

  return (
    <div>
      {/* Main card */}
      <Card className="my-6 w-full max-w-5xl mx-auto flex flex-col p-4 sm:p-6">
        {/* Header and Percentage */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 sm:gap-10 p-4">
          <h1 className="text-lg md:text-3xl font-semibold">
            Reconstructing Document Structures
          </h1>
          <h1 className="text-2xl md:text-3xl font-bold text-gray-900 self-end">
            {overallPercent}%{" "}
            <span className="text-sm text-muted-foreground">completed</span>
          </h1>
        </div>
        {/*Progress Bar Track  */}
        <div>
          <Progress
            value={overallPercent}
            className="bg-secondary mx-2 w-auto *:bg-primary"
          />
        </div>
        {/* Current Stage Callout */}
        <div className="flex flex-col sm:flex-row sm:items-center gap-1 p-4">
          <div className="flex items-center gap-1 shrink-0">
            {isFinalPhase ? (
              <CheckIcon className="w-4 h-4 text-primary" />
            ) : (
              <Spinner className="w-4 h-4 text-primary" />
            )}
            <p className="font-bold text-sm">
              Phase {currentStepIndex + 1} of {totalSteps}:
            </p>
          </div>
          <p className="text-sm text-muted-foreground">
            {steps[currentStepIndex]?.description}
          </p>
        </div>
        {/* Timeline Stepper */}
        <div className="-mx-4">
          <VerticalStepperDemo ref={stepperRef} onProgress={handleProgress} />
        </div>
        {/* Pipeline Footer */}
        <Card className="mx-2 my-6  bg-secondary">
          <div className="flex flex-row items-center gap-4 p-4">
            <div className="rounded-full bg-secondary p-2 shrink-0">
              <BadgeCheck className="text-primary" />
            </div>
            <div className="flex flex-col">
              <p className="font-bold">Native Flow Fidelity Guarantee</p>
              <p className="text-sm ">
                DocLift reconstructs actual Word document objects (tables,
                paragraphs, list definitions) rather than static text boxes.
                Once finished, document text reflows naturally when edited in
                Microsoft Word.
              </p>
            </div>
          </div>
        </Card>
        {/* Cancel Button */}
        <div className="flex items-center gap-4 p-4">
          <Button
            variant="danger"
            className="disabled:hover:text-inherit w-80"
            onClick={handleCancel}
            disabled={cancelled || done}
          >
            <X />
            {cancelled
              ? "Conversion Cancelled"
              : done
                ? "Conversion Completed"
                : "Cancel Conversion"}
          </Button>
        </div>
      </Card>
    </div>
  );
};

export default ProgressCard;
