import {
  useState,
  useEffect,
  useRef,
  forwardRef,
  useImperativeHandle,
  type ComponentType,
} from "react";
import {
  FileText,
  FolderArchive,
  Columns2,
  AlignVerticalSpaceAround,
  FileSearchCorner,
  PanelsTopLeft,
  Table,
} from "lucide-react";
import { toast } from "sonner";
import Stepper, { type Step, type StepTag } from "./Stepper";

export interface DemoStepTemplate {
  title: string;
  description: string;
  icon?: ComponentType<{ className?: string }>;
  completeMeta: string;
  completeTags?: StepTag[];
  runningNote?: string;
  progressLabel?: string;
  progressNote?: string;
  progressStats?: string[];
}

export const PIPELINE: DemoStepTemplate[] = [
  {
    title: "Document Ingestion & Verification",
    icon: FileSearchCorner,
    description: "Text stream validated, 14 pages parsed into forensic AST",
    completeMeta: "1.2s",
    completeTags: [{ label: "PDF/A-2b" }, { label: "Entropy score: 0.994" }],
    runningNote: "Validating byte stream",
    progressLabel: "Parsing page structure",
    progressNote: "pages parsed",
  },
  {
    title: "Reading Order & Layout Analysis",
    icon: PanelsTopLeft,
    description:
      "2-column flow & 28 paragraph blocks indexed; header/footer geometry isolated",
    completeMeta: "3.8s",
    completeTags: [
      { icon: Columns2, label: "2 Columns detected" },
      { icon: AlignVerticalSpaceAround, label: "Auto margins synced" },
    ],
    runningNote: "Indexing paragraph blocks",
    progressLabel: "Isolating header & footer geometry",
    progressNote: "blocks indexed",
  },
  {
    title: "Structure & Table Recovery",
    icon: Table,
    description:
      "Reconstructing 4 complex financial tables & cell alignments without text boxes.",
    completeMeta: "6.4s",
    completeTags: [{ label: "4 tables recovered" }],
    runningNote: "Running cell matrix solver",
    progressLabel: 'Table 2 of 4: "Statement of Comprehensive Income"',
    progressNote: "cells bounded",
    progressStats: [
      "Merged header spans: 3",
      "Decimal point alignment: Active",
    ],
  },
  {
    title: "Word (.docx) Model Synthesis",
    description:
      "Mapping native Word styles (Heading 1-3, Body, Table Grid definitions)",
    icon: FileText,
    completeMeta: "2.1s",
    completeTags: [{ label: "12 styles mapped" }],
    runningNote: "Writing style definitions",
    progressLabel: "Applying Table Grid definitions",
  },
  {
    title: "Media & Asset Packaging",
    description:
      "Embedding 7 high-res vector charts & imagery into OPC container",
    icon: FolderArchive,
    completeMeta: "0.9s",
    runningNote: "Compressing assets",
    progressLabel: "Embedding vector charts",
    progressNote: "assets embedded",
  },
];

export function useSimulatedPipeline(stepCount: number) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [percent, setPercent] = useState(0);
  const done = activeIndex >= stepCount;
  const [cancelled, setCancelled] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const advanceTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (cancelled) return;

    if (activeIndex >= stepCount) {
      return;
    }

    intervalRef.current = setInterval(() => {
      setPercent((current) => {
        const next = current + (6 + Math.random() * 10);

        if (next >= 100) {
          if (intervalRef.current) clearInterval(intervalRef.current);
          advanceTimeoutRef.current = setTimeout(() => {
            setActiveIndex((i) => i + 1);
            setPercent(0);
          }, 450);
          return 100;
        }

        return next;
      });
    }, 220);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
      if (advanceTimeoutRef.current) clearTimeout(advanceTimeoutRef.current);
    };
  }, [activeIndex, stepCount, cancelled]);

  const cancel = () => {
    if (done || cancelled) return;
    if (intervalRef.current) clearInterval(intervalRef.current);
    if (advanceTimeoutRef.current) clearTimeout(advanceTimeoutRef.current);
    setCancelled(true);
  };

  const reset = () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    if (advanceTimeoutRef.current) clearTimeout(advanceTimeoutRef.current);
    setCancelled(false);
    setPercent(0);
    setActiveIndex(0);
  };

  return { activeIndex, percent, done, cancelled, cancel, reset };
}

export interface StepperDemoHandle {
  cancel: () => void;
  complete: () => void;
  reset: () => void;
}

export interface PipelineProgress {
  activeIndex: number;
  totalSteps: number;
  currentStepPercent: number;
  overallPercent: number;
  done: boolean;
  cancelled?: boolean;
  failed?: boolean;
  error?: string;
}

export interface StepperDemoProps {
  onProgress?: (progress: PipelineProgress) => void;
}

export const StepperDemo = forwardRef<
  StepperDemoHandle,
  StepperDemoProps
>(function StepperDemo({ onProgress }, ref) {
  const { activeIndex, percent, done, cancelled, cancel, reset } =
    useSimulatedPipeline(PIPELINE.length);

  useImperativeHandle(
    ref,
    () => ({ cancel, complete: () => {}, reset }),
    [cancel, reset],
  );

  useEffect(() => {
    if (done) {
      toast.success("Conversion complete!");
    }
  }, [done]);

  const onProgressRef = useRef(onProgress);
  useEffect(() => {
    onProgressRef.current = onProgress;
  }, [onProgress]);

  useEffect(() => {
    const completedSteps = Math.min(activeIndex, PIPELINE.length);
    const overallPercent = done
      ? 100
      : Math.min(
          100,
          Math.round(((completedSteps + percent / 100) / PIPELINE.length) * 100),
        );

    onProgressRef.current?.({
      activeIndex,
      totalSteps: PIPELINE.length,
      currentStepPercent: percent,
      overallPercent,
      done,
      cancelled,
    });
  }, [activeIndex, percent, done, cancelled]);

  const steps: Step[] = PIPELINE.map((template, i) => {
    if (i < activeIndex) {
      return {
        title: template.title,
        description: template.description,
        status: "complete",
        meta: template.completeMeta,
        tags: template.completeTags,
      };
    }

    if (i === activeIndex) {
      return {
        title: template.title,
        description: template.description,
        status: cancelled ? "cancelled" : "loading",
        runningNote: template.runningNote,
        progress: {
          label: template.progressLabel ?? template.title,
          percent,
          note: template.progressNote,
          stats: template.progressStats,
        },
      };
    }

    return {
      title: template.title,
      description: template.description,
      status: "pending",
      icon: template.icon,
      meta: cancelled ? "Cancelled" : "Queued",
    };
  });

  return (
    <div className="flex min-h-140 w-full items-start px-4">
      <div className="w-full max-w-xl ">
        <div className="mb-6 flex items-center justify-between"></div>
        <Stepper steps={steps} />
      </div>
    </div>
  );
});

export default StepperDemo;
