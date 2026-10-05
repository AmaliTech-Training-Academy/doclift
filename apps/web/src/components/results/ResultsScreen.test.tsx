import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ResultsScreen from "./ResultsScreen";
import { ConversionProvider, useConversion } from "@/context/ConversionContext";
import { saveConversionSession } from "@/lib/conversionSession";

function TestWrapper() {
  const { file, resetKey, activeView, setFile, setActiveView } = useConversion();
  return (
    <div>
      <div data-testid="file-state">{file ? file.name : "no-file"}</div>
      <div data-testid="reset-key">{resetKey}</div>
      <div data-testid="active-view">{activeView}</div>
      <button
        onClick={() => {
          setFile(new File(["test"], "sample.pdf", { type: "application/pdf" }));
          setActiveView("result");
        }}
      >
        Set Result State
      </button>
      <ResultsScreen />
    </div>
  );
}

describe("ResultsScreen", () => {
  it("renders conversion complete and action buttons", () => {
    render(
      <ConversionProvider>
        <ResultsScreen />
      </ConversionProvider>,
    );

    expect(screen.getByText("Conversion Complete")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /convert another file/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /download/i }),
    ).not.toBeInTheDocument();
  });

  it("shows the output file size and conversion time from the session", async () => {
    saveConversionSession({
      jobId: "job-123",
      fileName: "report.pdf",
      status: "done",
      updatedAt: Date.now(),
      durationSeconds: 17.6,
      fileSize: 5 * 1024 * 1024,
      outputSizeBytes: 48213,
    });

    render(
      <ConversionProvider>
        <ResultsScreen />
      </ConversionProvider>,
    );

    expect(
      await screen.findByText("47.1 KB • 18s conversion time"),
    ).toBeInTheDocument();
  });

  it("renders the download button when a completed session has a jobId", async () => {
    saveConversionSession({
      jobId: "job-123",
      fileName: "report.pdf",
      status: "done",
      updatedAt: Date.now(),
    });

    render(
      <ConversionProvider>
        <ResultsScreen />
      </ConversionProvider>,
    );

    expect(
      await screen.findByRole("button", { name: /download/i }),
    ).toBeInTheDocument();
    expect(screen.getByText("report.docx")).toBeInTheDocument();
  });

  it("calls reset() when 'Convert Another File' is clicked, resetting app state and view", async () => {
    const user = userEvent.setup();
    render(
      <ConversionProvider>
        <TestWrapper />
      </ConversionProvider>,
    );

    // Set initial file and result state
    await user.click(screen.getByRole("button", { name: "Set Result State" }));
    expect(screen.getByTestId("file-state")).toHaveTextContent("sample.pdf");
    expect(screen.getByTestId("reset-key")).toHaveTextContent("0");
    expect(screen.getByTestId("active-view")).toHaveTextContent("result");

    // Click Convert Another File
    await user.click(screen.getByRole("button", { name: /convert another file/i }));

    // Verify reset() was called: file cleared, resetKey incremented, view set to upload
    expect(screen.getByTestId("file-state")).toHaveTextContent("no-file");
    expect(screen.getByTestId("reset-key")).toHaveTextContent("1");
    expect(screen.getByTestId("active-view")).toHaveTextContent("upload");
  });
});
