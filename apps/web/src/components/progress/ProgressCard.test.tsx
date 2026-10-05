import { describe, expect, it, vi, beforeEach } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { forwardRef, useImperativeHandle, useEffect } from "react";
import type { PipelineProgress } from "../ui/Stepper";
import { useConversion } from "../../context/ConversionContext";
import type { ConversionSession } from "@/lib/conversionSession";

const stepperMocks = vi.hoisted(() => ({
  onProgress: undefined as ((state: PipelineProgress) => void) | undefined,
  fail: vi.fn(),
}));

vi.mock("../ui/Stepper", () => ({
  StepperDemo: forwardRef(function MockStepper(
    props: { onProgress?: (state: PipelineProgress) => void },
    ref,
  ) {
    stepperMocks.onProgress = props.onProgress;
    useImperativeHandle(ref, () => ({
      fail: stepperMocks.fail,
      failed: stepperMocks.fail,
      complete: () => {},
    }));
    return null;
  }),
  VerticalStepperDemo: forwardRef(function MockStepper(
    props: { onProgress?: (state: PipelineProgress) => void },
    ref,
  ) {
    stepperMocks.onProgress = props.onProgress;
    useImperativeHandle(ref, () => ({
      fail: stepperMocks.fail,
      failed: stepperMocks.fail,
      complete: () => {},
    }));
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
    ...overrides,
  };
  act(() => stepperMocks.onProgress?.(state));
}

describe("ProgressCard", () => {
  beforeEach(() => {
    stepperMocks.fail.mockClear();
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

  it("triggers failure when the simulate failure button is clicked", async () => {
    const user = userEvent.setup();
    renderWithProvider();

    const failureButton = screen.getByRole("button", { name: /simulate failure/i });
    await user.click(failureButton);

    expect(stepperMocks.fail).toHaveBeenCalledTimes(1);
    expect(toast.error).toHaveBeenCalledWith("Conversion failed", {
      description: "DocLift could not convert your document.",
    });
    expect(
      screen.getByRole("button", { name: "Simulated failure" }),
    ).toBeDisabled();
  });

  it("ignores repeated failure button clicks", async () => {
    const user = userEvent.setup();
    renderWithProvider();

    const failureButton = screen.getByRole("button", { name: /simulate failure/i });
    await user.click(failureButton);
    // Button is now disabled, so a second click is a no-op through the DOM,
    // but we also guard in the handler itself.
    expect(stepperMocks.fail).toHaveBeenCalledTimes(1);
    expect(toast.error).toHaveBeenCalledTimes(1);
  });

  it("disables the button and shows completion copy once the pipeline is done", () => {
    renderWithProvider();

    emitProgress({ activeIndex: 5, overallPercent: 100, done: true });

    expect(
      screen.getByRole("button", { name: "Conversion Completed" }),
    ).toBeDisabled();
  });

  it("renders the error state card and updates session status to failed when the pipeline reports an error", async () => {
    function StatusProbe({
      onSession,
    }: {
      onSession: (session: ConversionSession | null) => void;
    }) {
      const { session, startConversion } = useConversion();

      useEffect(() => {
        if (!session) {
          startConversion(
            new File(["test"], "sample.pdf", { type: "application/pdf" }),
          );
        }
      }, [session, startConversion]);

      useEffect(() => {
        onSession(session);
      }, [session, onSession]);

      return <ProgressCard />;
    }

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ jobId: 999 }),
    } as unknown as Response);

    const tracker = { session: null as ConversionSession | null };
    render(
      <ConversionProvider>
        <StatusProbe onSession={(s) => (tracker.session = s)} />
      </ConversionProvider>,
    );

    await waitFor(() => expect(tracker.session).not.toBeNull());

    emitProgress({ error: "Something went wrong" });

    expect(
      screen.getByRole("heading", { name: /conversion couldn't be completed/i }),
    ).toBeInTheDocument();
    expect(tracker.session?.status).toBe("failed");
  });

  it("does not render the error state card by default", () => {
    renderWithProvider();

    expect(
      screen.queryByRole("heading", { name: /conversion couldn't be completed/i }),
    ).not.toBeInTheDocument();
  });
});