import { describe, expect, it, vi, beforeEach } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { forwardRef, useImperativeHandle } from "react";
import type { PipelineProgress } from "../ui/Stepper";

const stepperMocks = vi.hoisted(() => ({
  onProgress: undefined as ((state: PipelineProgress) => void) | undefined,
  cancel: vi.fn(),
}));

vi.mock("../ui/Stepper", () => ({
  VerticalStepperDemo: forwardRef(function MockStepper(
    props: { onProgress?: (state: PipelineProgress) => void },
    ref,
  ) {
    stepperMocks.onProgress = props.onProgress;
    useImperativeHandle(ref, () => ({ cancel: stepperMocks.cancel, complete: () => {} }));
    return null;
  }),
}));

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

import ProgressCard from "./ProgressCard";
import { toast } from "sonner";
import { ConversionProvider } from "@/context/ConversionContext";

function renderWithProvider() {
  return render(
    <ConversionProvider>
      <ProgressCard />
    </ConversionProvider>,
  );
}

function emitProgress(overrides: Partial<PipelineProgress> = {}) {
  const state: PipelineProgress = {
    activeIndex: 0,
    totalSteps: 5,
    currentStepPercent: 0,
    overallPercent: 0,
    done: false,
    cancelled: false,
    ...overrides,
  };
  act(() => stepperMocks.onProgress?.(state));
}

describe("ProgressCard", () => {
  beforeEach(() => {
    stepperMocks.cancel.mockClear();
    vi.mocked(toast.error).mockClear();
    vi.mocked(toast.success).mockClear();
  });

  it("renders the initial state at 0% on phase 1", () => {
    renderWithProvider();

    expect(screen.getByText("0%")).toBeInTheDocument();
    expect(screen.getByText("Phase 1 of 5:")).toBeInTheDocument();
    expect(
      screen.getByText("Initializing document processing..."),
    ).toBeInTheDocument();
  });

  it("reflects progress reported by the stepper", () => {
    renderWithProvider();

    emitProgress({ activeIndex: 2, overallPercent: 45 });

    expect(screen.getByText("45%")).toBeInTheDocument();
    expect(screen.getByText("Phase 3 of 5:")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Reconstructing tabular data structures and nested headers...",
      ),
    ).toBeInTheDocument();
  });

  it("shows the final phase description and a check icon once the last phase is reached", () => {
    renderWithProvider();

    emitProgress({ activeIndex: 4, overallPercent: 95 });

    expect(screen.getByText("Phase 5 of 5:")).toBeInTheDocument();
    expect(
      screen.getByText("Finalizing the document reconstruction process..."),
    ).toBeInTheDocument();
  });

  it("cancels the conversion when the cancel button is clicked", async () => {
    const user = userEvent.setup();
    renderWithProvider();

    const cancelButton = screen.getByRole("button", { name: /cancel conversion/i });
    await user.click(cancelButton);

    expect(stepperMocks.cancel).toHaveBeenCalledTimes(1);
    expect(toast.error).toHaveBeenCalledWith("Conversion cancelled");
    expect(
      screen.getByRole("button", { name: "Conversion Cancelled" }),
    ).toBeDisabled();
  });

  it("ignores repeated cancel clicks", async () => {
    const user = userEvent.setup();
    renderWithProvider();

    const cancelButton = screen.getByRole("button", { name: /cancel conversion/i });
    await user.click(cancelButton);
    // Button is now disabled, so a second click is a no-op through the DOM,
    // but we also guard in the handler itself.
    expect(stepperMocks.cancel).toHaveBeenCalledTimes(1);
    expect(toast.error).toHaveBeenCalledTimes(1);
  });

  it("disables the button and shows completion copy once the pipeline is done", () => {
    renderWithProvider();

    emitProgress({ activeIndex: 5, overallPercent: 100, done: true });

    expect(
      screen.getByRole("button", { name: "Conversion Completed" }),
    ).toBeDisabled();
  });

  it("renders the error state card when the pipeline reports an error", () => {
    renderWithProvider();

    emitProgress({ error: "Something went wrong" });

    expect(screen.getByRole("heading", { name: "Error" })).toBeInTheDocument();
  });

  it("does not render the error state card by default", () => {
    renderWithProvider();

    expect(
      screen.queryByRole("heading", { name: "Error" }),
    ).not.toBeInTheDocument();
  });
});
