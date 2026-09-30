import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { createRef } from "react";
import StepperDemo, {
  type StepperDemoHandle,
  type PipelineProgress,
} from "./StepperDemo";

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

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

describe("StepperDemo", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it("starts on the first step at 0% and reports initial progress", () => {
    const onProgress = vi.fn();
    render(<StepperDemo onProgress={onProgress} />);

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
    render(<StepperDemo onProgress={onProgress} />);

    await advanceTime(220 * 3);

    const latest = onProgress.mock.calls.at(-1)?.[0] as PipelineProgress;
    expect(latest.currentStepPercent).toBeGreaterThan(0);
  });

  it("stops progressing and reports cancelled after cancel() is called via the ref", async () => {
    const onProgress = vi.fn();
    const ref = createRef<StepperDemoHandle>();
    render(<StepperDemo ref={ref} onProgress={onProgress} />);

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
    render(<StepperDemo onProgress={onProgress} />);
    await advanceTime(60000);

    const latest = onProgress.mock.calls.at(-1)?.[0] as PipelineProgress;
    expect(latest.done).toBe(true);
    expect(latest.overallPercent).toBe(100);
  });
});
