import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { createRef } from "react";
import VerticalStepper, {
  VerticalStepperDemo,
  type Step,
  type VerticalStepperDemoHandle,
  type PipelineProgress,
} from "./Stepper";

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

// Advances the fake clock in small, individually-awaited increments (one
// interval tick at a time) rather than one large jump. Jumping the whole
// duration in a single `advanceTimersByTimeAsync` call fires many interval
// ticks back-to-back before React gets a chance to flush the state/effect
// updates chained off each one (the pipeline clears+reschedules its own
// timers from inside a setState updater), which starves later ticks. Real
// browsers never hit this because ticks are naturally spaced out.
async function advanceTime(totalMs: number, stepMs = 220) {
  let remaining = totalMs;
  while (remaining > 0) {
    const chunk = Math.min(stepMs, remaining);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(chunk);
    });
    remaining -= chunk;
  }
}

describe("VerticalStepper", () => {
  const steps: Step[] = [
    {
      title: "Ingest",
      description: "Reading file",
      status: "complete",
      meta: "1.2s",
      tags: [{ label: "PDF/A-2b" }],
    },
    {
      title: "Analyze",
      description: "Finding tables",
      status: "loading",
      runningNote: "Running solver",
      progress: { label: "Table 1 of 2", percent: 40, note: "cells bounded" },
    },
    {
      title: "Package",
      description: "Writing docx",
      status: "pending",
      meta: "Queued",
    },
  ];

  it("renders a row per step with its title and description", () => {
    render(<VerticalStepper steps={steps} />);

    expect(screen.getByText("Ingest")).toBeInTheDocument();
    expect(screen.getByText("Analyze")).toBeInTheDocument();
    expect(screen.getByText("Package")).toBeInTheDocument();
    expect(screen.getByText("Reading file")).toBeInTheDocument();
    expect(screen.getByText("Writing docx")).toBeInTheDocument();
  });

  it("shows meta pills and tags for completed/pending steps", () => {
    render(<VerticalStepper steps={steps} />);

    expect(screen.getByText("1.2s")).toBeInTheDocument();
    expect(screen.getByText("Queued")).toBeInTheDocument();
    expect(screen.getByText("PDF/A-2b")).toBeInTheDocument();
  });

  it("expands the active (loading) step into a detail card with a progress panel", () => {
    render(<VerticalStepper steps={steps} />);

    expect(screen.getByText("In Progress")).toBeInTheDocument();
    expect(screen.getByText("Running solver")).toBeInTheDocument();
    expect(screen.getByText("Table 1 of 2")).toBeInTheDocument();
    expect(screen.getByText("40% cells bounded")).toBeInTheDocument();
  });

  it("marks a cancelled step as such instead of in progress", () => {
    const cancelledSteps: Step[] = [
      { ...steps[1], status: "cancelled" },
    ];
    render(<VerticalStepper steps={cancelledSteps} />);

    expect(screen.getByText("Cancelled")).toBeInTheDocument();
    expect(screen.queryByText("In Progress")).not.toBeInTheDocument();
  });

  it("renders its built-in default steps when no steps prop is given", () => {
    render(<VerticalStepper />);

    expect(
      screen.getByText("Document Ingestion & Verification"),
    ).toBeInTheDocument();
  });
});

describe("VerticalStepperDemo", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it("starts on the first step at 0% and reports initial progress", () => {
    const onProgress = vi.fn();
    render(<VerticalStepperDemo onProgress={onProgress} />);

    expect(
      screen.getByText("Document Ingestion & Verification"),
    ).toBeInTheDocument();

    const firstCall = onProgress.mock.calls[0][0] as PipelineProgress;
    expect(firstCall.activeIndex).toBe(0);
    expect(firstCall.done).toBe(false);
    expect(firstCall.cancelled).toBe(false);
  });

  it("advances progress over time via onProgress", async () => {
    const onProgress = vi.fn();
    render(<VerticalStepperDemo onProgress={onProgress} />);

    await advanceTime(220 * 3);

    const latest = onProgress.mock.calls.at(-1)?.[0] as PipelineProgress;
    expect(latest.currentStepPercent).toBeGreaterThan(0);
  });

  it("stops progressing and reports cancelled after cancel() is called via the ref", async () => {
    const onProgress = vi.fn();
    const ref = createRef<VerticalStepperDemoHandle>();
    render(<VerticalStepperDemo ref={ref} onProgress={onProgress} />);

    await advanceTime(220 * 2);

    act(() => {
      ref.current?.cancel();
    });

    const afterCancel = onProgress.mock.calls.at(-1)?.[0] as PipelineProgress;
    expect(afterCancel.cancelled).toBe(true);

    const percentAtCancel = afterCancel.currentStepPercent;

    await advanceTime(5000);

    const afterMoreTime = onProgress.mock.calls.at(-1)?.[0] as PipelineProgress;
    expect(afterMoreTime.currentStepPercent).toBe(percentAtCancel);
    expect(afterMoreTime.cancelled).toBe(true);
  });

  it("reaches done=true once every step completes, and reports 100% overall", async () => {
    const onProgress = vi.fn();
    render(<VerticalStepperDemo onProgress={onProgress} />);

    // 5 steps * (up to ~17 ticks of 220ms to reach 100% + 450ms pause) is
    // comfortably covered by this time budget.
    await advanceTime(60000);

    const latest = onProgress.mock.calls.at(-1)?.[0] as PipelineProgress;
    expect(latest.done).toBe(true);
    expect(latest.overallPercent).toBe(100);
  });
});
