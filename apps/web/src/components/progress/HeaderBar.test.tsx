import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import HeaderBar from "./HeaderBar";
import { ConversionProvider, useConversion } from "@/context/ConversionContext";

function renderWithProvider(ui: React.ReactElement) {
  return render(<ConversionProvider>{ui}</ConversionProvider>);
}

describe("HeaderBar", () => {
  it("shows placeholder copy when no file is selected", () => {
    renderWithProvider(<HeaderBar file={null} timeElapsed={0} timeRemaining={0} />);

    expect(screen.getAllByText("Untitled document").length).toBeGreaterThan(0);
    expect(screen.getAllByText("No file selected").length).toBeGreaterThan(0);
    expect(screen.getByText("Untitled document.docx")).toBeInTheDocument();
  });

  it("derives source and target file info from the given file", () => {
    const file = new File(["%PDF-1.4"], "quarterly-report.pdf", {
      type: "application/pdf",
    });
    Object.defineProperty(file, "size", { value: 2.5 * 1024 * 1024 });

    renderWithProvider(<HeaderBar file={file} timeElapsed={0} timeRemaining={0} />);

    expect(screen.getByText("quarterly-report.pdf")).toBeInTheDocument();
    expect(screen.getByText("quarterly-report.docx")).toBeInTheDocument();
    expect(screen.getByText("PDF Document • 2.5 MB")).toBeInTheDocument();
    expect(screen.getByText("Word Document (.docx)")).toBeInTheDocument();
  });

  it("formats elapsed and remaining time as mm:ss", () => {
    renderWithProvider(
      <HeaderBar file={null} timeElapsed={65} timeRemaining={90} />,
    );

    expect(screen.getByText("1:05 elapsed")).toBeInTheDocument();
    expect(screen.getByText("~1:30s remaining")).toBeInTheDocument();
  });

  it("switches to the result view when the target file is clicked", async () => {
    const user = userEvent.setup();

    function ActiveViewProbe() {
      const { activeView } = useConversion();
      return <p>active:{activeView}</p>;
    }

    renderWithProvider(
      <>
        <HeaderBar file={null} timeElapsed={0} timeRemaining={0} />
        <ActiveViewProbe />
      </>,
    );

    expect(screen.getByText("active:upload")).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "Open Untitled document.docx" }),
    );

    expect(screen.getByText("active:result")).toBeInTheDocument();
  });
});
