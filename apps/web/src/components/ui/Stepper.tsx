import {
  useState,
  useEffect,
  useRef,
  forwardRef,
  useImperativeHandle,
  type ComponentType,
} from "react";
import {
  Check,
  Loader2,
  AlertCircle,
  FileText,
  FolderArchive,
  Columns2,
  AlignVerticalSpaceAround,
  X,
  FileSearchCorner,
  PanelsTopLeft,
  Table,
} from "lucide-react";
import { toast } from "sonner";

// The states a step can be in. "error" is a failed step; "cancelled" is a
// step whose run was stopped part-way through by the user.
export type StepStatus =
  | "pending"
  | "loading"
  | "complete"
  | "error"
  | "cancelled";

// A small labeled chip shown under a step's description (e.g. "PDF/A-2b",
// "2 Columns detected"). The icon is optional — plain text chips are fine.
export interface StepTag {
  icon?: ComponentType<{ className?: string }>;
  label: string;
}

// Extra detail panel shown ONLY while a step is "loading" — this is what
// produces the highlighted progress bar + stats block from the reference
// design (e.g. "Table 2 of 4 ... 88% cells bounded").
export interface StepProgress {
  label: string; // left-aligned context, e.g. 'Table 2 of 4: "Statement of Income"'
  percent: number; // 0-100, drives the bar width
  note?: string; // short text appended after the percentage, e.g. "cells bounded"
  stats?: string[]; // small bullet facts shown below the bar
}

// Shape of a single step. Everything the component renders is derived from
// this — the parent app is responsible for updating `status` (and, while
// loading, `progress.percent`) as real work completes.
export interface Step {
  title: string;
  description: string;
  status: StepStatus;
  icon?: ComponentType<{ className?: string }>; // content icon shown while pending
  meta?: string; // right-aligned pill, e.g. "1.2s" once complete, or "Queued"
  runningNote?: string; // small italic note top-right while loading, e.g. "Running cell matrix solver"
  tags?: StepTag[]; // small chips under the description, typically once complete
  progress?: StepProgress; // detail panel — only rendered when status is "loading"
}

/**
 * VerticalStepper
 * ----------------
 * A vertical progress stepper modeled on a document-processing pipeline UI:
 * completed steps are quiet with a check + small chips, the active step
 * expands into a highlighted card with a progress bar and live stats, and
 * upcoming steps sit muted with a "Queued" pill until their turn comes.
 *
 * Usage:
 *   <VerticalStepper steps={[
 *     { title: "Ingest", description: "...", status: "complete", meta: "1.2s" },
 *     { title: "Analyze", description: "...", status: "loading",
 *       progress: { label: "Table 2 of 4", percent: 88, stats: ["Merged spans: 3"] } },
 *     { title: "Package", description: "...", status: "pending", meta: "Queued" },
 *   ]} />
 */
interface StepIconProps {
  status: StepStatus;
  icon?: ComponentType<{ className?: string }>; // used only while pending
}

// Renders just the circular icon to the left of a step's text. Kept
// separate from VerticalStepper so the "what icon for what status" logic
// doesn't clutter the layout code below.
function StepIcon({ status, icon: ContentIcon }: StepIconProps) {
  // Shared sizing/shape/transition classes — only background + icon differ.
  const base =
    "relative z-10 flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition-colors duration-300 ml-4";

  // Completed: soft tinted circle with a dark check — deliberately NOT a
  // loud solid-green circle, so a long list of finished steps stays calm.
  if (status === "complete") {
    return (
      <div className={`${base} bg-brand-secondary`}>
        <Check className="h-4 w-4 text-brand-primary" strokeWidth={3} />
      </div>
    );
  }

  // In progress: the one saturated circle in the whole list, so the eye
  // goes straight to whichever step is currently running.
  if (status === "loading") {
    return (
      <div className={`${base} bg-brand-primary`}>
        <Loader2
          className="h-4 w-4 animate-spin text-white"
          strokeWidth={2.5}
        />
      </div>
    );
  }

  // Failed: solid red circle with an alert glyph.
  if (status === "error") {
    return (
      <div className={`${base} bg-rose-500`}>
        <AlertCircle className="h-4 w-4 text-white" strokeWidth={2.5} />
      </div>
    );
  }

  // Cancelled: muted slate circle with an X — distinct from "error" since
  // the user stopped it, the pipeline didn't fail on its own.
  if (status === "cancelled") {
    return (
      <div className={`${base} bg-slate-300`}>
        <X className="h-4 w-4 text-slate-600" strokeWidth={2.5} />
      </div>
    );
  }

  // Pending: shows the step's own content icon (e.g. a document or folder
  // glyph) in a muted grey circle, so upcoming steps hint at what they do
  // without competing for attention. Falls back to a plain dot if the
  // caller didn't supply an icon for this step.
  return (
    <div className={`${base} bg-slate-100`}>
      {ContentIcon ? (
        <ContentIcon className="h-4 w-4 text-slate-400" />
      ) : (
        <span className="h-2 w-2 rounded-full bg-slate-300" />
      )}
    </div>
  );
}

interface ConnectorProps {
  status: StepStatus; // status of the step ABOVE this connector, not below
}

// The vertical line drawn between one step's icon and the next. A grey
// track sits underneath; an indigo overlay is scaled to 0%, 50%, or 100%
// height on top of it to show how far progress has reached.
function Connector({ status }: ConnectorProps) {
  const filled = status === "complete"; // previous step finished -> full line
  const active = status === "loading"; // previous step in progress -> half-fills

  return (
    // ml-[18px] centers the 2px line under the 36px (h-9 w-9) icon above it.
    <div className="relative ml-[18px] h-full w-0.5 flex-1 bg-slate-200">
      <div
        className={`absolute inset-x-0 top-0 w-full origin-top bg-brand-primary transition-transform duration-500 ease-out ${
          filled ? "scale-y-100" : active ? "scale-y-50" : "scale-y-0"
        }`}
        style={{ height: "100%" }}
      />
    </div>
  );
}

// The plain, single-line layout used for "complete" and "pending" steps:
// title + description on the left, an optional row of tag chips beneath,
// and an optional meta pill (time elapsed, or "Queued") on the right.
function StepRow({ step }: { step: Step }) {
  const muted = step.status === "pending";

  return (
    <div className="flex items-start justify-between md:w-200">
      <div className="min-w-0">
        <p
          className={`text-sm md:text-2xl font-semibold ${muted ? "text-slate-400" : "text-slate-900"}`}
        >
          {step.title}
        </p>
        <p
          className={`mt-1 text-sm ${muted ? "text-slate-300" : "text-slate-500"}`}
        >
          {step.description}
        </p>

        {step.tags && step.tags.length > 0 && (
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {step.tags.map((tag, i) => {
              const TagIcon = tag.icon;
              return (
                <span
                  key={i}
                  className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${
                    muted
                      ? "bg-slate-50 text-slate-400"
                      : "bg-indigo-50 text-indigo-600"
                  }`}
                >
                  {TagIcon && <TagIcon className="h-3 w-3" />}
                  {tag.label}
                </span>
              );
            })}
          </div>
        )}
      </div>

      {step.meta && (
        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs  text-slate-500">
          {step.meta}
        </span>
      )}
    </div>
  );
}

// The expanded layout used ONLY for the currently "loading" step: a tinted
// card with an "In Progress" badge, an optional running note, and (if
// `progress` was supplied) a white inset panel with a percentage, a bar,
// and small stat bullets — this is the part that mirrors the reference image.
function ActiveStepCard({ step }: { step: Step }) {
  const { title, description, runningNote, progress } = step;
  const cancelled = step.status === "cancelled";

  return (
    <div
      className={`rounded-xl border md:w-200 p-4 ${
        cancelled
          ? "border-slate-200 bg-slate-50"
          : "border-brand-secondary bg-brand-secondary"
      }`}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <p
            className={`text-base font-semibold md:text-3xl ${cancelled ? "text-slate-600" : "text-brand-primary"}`}
          >
            {title}
          </p>
          <span
            className={`rounded-full px-2 py-0.5 text-xs truncate font-medium ${
              cancelled
                ? "bg-slate-200 text-slate-600"
                : "bg-indigo-100 text-brand-primary"
            }`}
          >
            {cancelled ? "Cancelled" : "In Progress"}
          </span>
        </div>
        {!cancelled && runningNote && (
          <span className="text-xs italic text-brand-primary">{runningNote}</span>
        )}
      </div>

      <p
        className={`mt-1 text-sm ${cancelled ? "text-slate-500" : "text-indigo-900/70"}`}
      >
        {description}
      </p>

      {progress && (
        <div className="mt-3 rounded-lg bg-white p-3">
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
            <span className="text-slate-600">{progress.label}</span>
            <span
              className={`font-semibold ${cancelled ? "text-slate-500" : "text-brand-primary"}`}
            >
              {Math.round(progress.percent)}%
              {progress.note ? ` ${progress.note}` : ""}
            </span>
          </div>

          {/* Track + fill: fill width is driven directly by `percent`, so
              the caller just updates that number as real work reports in.
              Left frozen at its last value once cancelled. */}
          <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-brand-secondary">
            <div
              className={`h-full rounded-full transition-all duration-300 ease-out ${
                cancelled ? "bg-slate-400" : "bg-brand-primary"
              }`}
              style={{
                width: `${Math.min(100, Math.max(0, progress.percent))}%`,
              }}
            />
          </div>

          {progress.stats && progress.stats.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1">
              {progress.stats.map((stat, i) => (
                <span
                  key={i}
                  className="flex items-center gap-1.5 text-xs text-brand-primary"
                >
                  <span className="h-1 w-1 rounded-full bg-brand-primary" />
                  {stat}
                </span>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export interface VerticalStepperProps {
  // Optional so the component can render a sensible preview on its own
  // (see DEFAULT_STEPS) when no data is passed in yet.
  steps?: Step[];
}

// Fallback content shown if no `steps` prop is supplied — mainly useful
// for previewing the component in isolation (e.g. in Storybook).
const DEFAULT_STEPS: Step[] = [
  {
    title: "Document Ingestion & Verification",
    description: "Text stream validated, 14 pages parsed into forensic AST",
    status: "complete",
    meta: "1.2s",
    tags: [{ label: "PDF/A-2b" }, { label: "Entropy score: 0.994" }],
  },
  {
    title: "Structure & Table Recovery",
    description:
      "Reconstructing 4 complex financial tables & cell alignments without text boxes.",
    status: "loading",
    runningNote: "Running cell matrix solver",
    progress: {
      label: 'Table 2 of 4: "Statement of Comprehensive Income"',
      percent: 88,
      note: "cells bounded",
      stats: ["Merged header spans: 3", "Decimal point alignment: Active"],
    },
  },
  {
    title: "Word (.docx) Model Synthesis",
    description:
      "Mapping native Word styles (Heading 1-3, Body, Table Grid definitions)",
    status: "pending",
    icon: FileText,
    meta: "Queued",
  },
];

// The main exported component. It is purely presentational — it holds no
// internal state and does not decide when a step moves from "loading" to
// "complete". The parent app owns that logic and just re-renders this
// component with updated `status` (and `progress.percent`) values.
export default function VerticalStepper({
  steps: stepsProp,
}: VerticalStepperProps) {
  const steps = stepsProp ?? DEFAULT_STEPS;

  return (
    <ol className="w-full max-w-xl">
      {steps.map((step, i) => {
        const isLast = i === steps.length - 1; // last step has no connector below it

        return (
          <li key={i} className="relative flex gap-4 pb-8 last:pb-0">
            {/* Left column: icon stacked on top of the connecting line */}
            <div className="flex flex-col items-center">
              <StepIcon status={step.status} icon={step.icon} />
              {!isLast && (
                <div className="mt-1 flex-1">
                  <Connector status={step.status} />
                </div>
              )}
            </div>

            {/* Right column: the plain row for complete/pending steps, or
                the expanded highlighted card for the one active step. */}
            <div className="min-w-0 flex-1 pt-1">
              {step.status === "loading" || step.status === "cancelled" ? (
                <ActiveStepCard step={step} />
              ) : (
                <StepRow step={step} />
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}

/**
 * ---------------------------------------------------------------------
 * Everything below this line is a DEMO, not part of the reusable component.
 * It simulates a 4-step document pipeline: each step's progress bar climbs
 * from 0 to 100 on its own, then the step marks complete and the next one
 * starts "loading" — mirroring how a real app would report async progress.
 * ---------------------------------------------------------------------
 */

// Static per-step content that doesn't change as the demo runs — only
// `status` and `progress.percent` are computed live from `activeIndex`
// and `percent` (see useSimulatedPipeline below).
interface DemoStepTemplate {
  title: string;
  description: string;
  icon?: ComponentType<{ className?: string }>; // shown while this step is still pending
  completeMeta: string; // time pill shown once this step finishes, e.g. "1.2s"
  completeTags?: StepTag[]; // chips shown once this step finishes
  runningNote?: string; // shown while this step is loading
  progressLabel?: string; // left side of the progress panel while loading
  progressNote?: string; // short suffix after the percentage, e.g. "cells bounded"
  progressStats?: string[]; // bullet facts shown while loading
}

const PIPELINE: DemoStepTemplate[] = [
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

// Drives `activeIndex` (which step is currently "loading") and `percent`
// (that step's progress, 0-100). Every tick nudges percent forward by a
// random amount; on reaching 100 it pauses briefly, then advances to the
// next step with percent reset to 0. When every step has been passed,
// `done` flips to true and the interval stops itself. Calling `cancel`
// stops the timers immediately and freezes progress where it stood.
function useSimulatedPipeline(stepCount: number) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [percent, setPercent] = useState(0);
  const [done, setDone] = useState(false);
  const [cancelled, setCancelled] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const advanceTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (cancelled) return; // stopped by the user — don't start/continue ticking

    if (activeIndex >= stepCount) {
      setDone(true);
      return;
    }

    intervalRef.current = setInterval(() => {
      setPercent((current) => {
        const next = current + (6 + Math.random() * 10); // uneven, realistic-looking steps

        if (next >= 100) {
          // Stop ticking this step, hold at 100% briefly, then move on.
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
    setDone(false);
    setCancelled(false);
    setPercent(0);
    setActiveIndex(0);
  };

  return { activeIndex, percent, done, cancelled, cancel, reset };
}

// Imperative handle exposed on VerticalStepperDemo so a parent (e.g. a
// "Cancel Conversion" button that lives outside this component) can stop
// the running simulation without lifting all of its state.
export interface VerticalStepperDemoHandle {
  cancel: () => void;
  complete: () => void;
}

// Overall pipeline progress, reported to the parent via `onProgress` so
// things outside the stepper (the top summary bar, a "Cancel" button) can
// stay in sync with what the steps are actually doing.
export interface PipelineProgress {
  activeIndex: number;
  totalSteps: number;
  currentStepPercent: number; // 0-100, progress of just the active step
  overallPercent: number; // 0-100, progress across the whole pipeline
  done: boolean;
  cancelled: boolean;
  error?: string; // optional error message if the pipeline failed
}

export interface VerticalStepperDemoProps {
  onProgress?: (progress: PipelineProgress) => void;
}

// The actual demo component: turns PIPELINE + the live simulation state
// into full Step objects and hands them to VerticalStepper.
export const VerticalStepperDemo = forwardRef<
  VerticalStepperDemoHandle,
  VerticalStepperDemoProps
>(function VerticalStepperDemo({ onProgress }, ref) {
    const { activeIndex, percent, done, cancelled, cancel } =
      useSimulatedPipeline(PIPELINE.length);

    useImperativeHandle(ref, () => ({ cancel, complete: () => { } }), [cancel]);

    useEffect(() => {
      if (done) {
        toast.success("Conversion complete!");
      }
    }, [done]);

    // Kept in a ref so this effect can depend only on the progress values
    // themselves — an inline `onProgress` from the parent would otherwise
    // change identity every render and re-fire the effect needlessly.
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
        // Already finished: show the check, elapsed time, and its tags.
        return {
          title: template.title,
          description: template.description,
          status: "complete",
          meta: template.completeMeta,
          tags: template.completeTags,
        };
      }

      if (i === activeIndex) {
        // Currently running (or just stopped): expand into the highlighted
        // card with a live — or frozen, once cancelled — progress bar.
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

      // Not started yet: muted row with its content icon and a pill that
      // reflects whether it's still queued or will never run now.
      return {
        title: template.title,
        description: template.description,
        status: "pending",
        icon: template.icon,
        meta: cancelled ? "Cancelled" : "Queued",
      };
    });

    return (
      <div className="flex min-h-[560px] w-full items-start px-4">
        <div className="w-full max-w-xl ">
          <div className="mb-6 flex items-center justify-between"></div>
          <VerticalStepper steps={steps} />
        </div>
      </div>
    );
  },
);
