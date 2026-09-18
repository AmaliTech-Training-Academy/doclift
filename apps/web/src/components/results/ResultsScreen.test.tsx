import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ResultsScreen from "./ResultsScreen";
import { ConversionProvider, useConversion } from "@/context/ConversionContext";

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
      screen.getByRole("button", { name: /download word document/i }),
    ).toBeInTheDocument();
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
