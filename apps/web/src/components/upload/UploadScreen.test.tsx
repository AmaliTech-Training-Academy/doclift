import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import UploadScreen from "./UploadScreen";
import { ConversionProvider, useConversion } from "@/context/ConversionContext";

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("pdfjs-dist", () => ({
  GlobalWorkerOptions: {},
  getDocument: vi.fn(() => ({ promise: Promise.resolve({ numPages: 3 }) })),
}));

function renderWithProvider() {
  return render(
    <ConversionProvider>
      <UploadScreen />
    </ConversionProvider>,
  );
}

function getFileInput(container: HTMLElement) {
  return container.querySelector('input[type="file"]') as HTMLInputElement;
}

function makeValidPdf(name = "report.pdf") {
  return new File(["%PDF-1.4\nsome pdf bytes"], name, {
    type: "application/pdf",
  });
}

describe("UploadScreen", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the hero copy and an empty drop zone with no selected file", () => {
    renderWithProvider();

    expect(
      screen.getByRole("heading", {
        name: /transform your pdf into editable word document/i,
      }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/PDF selected/i)).not.toBeInTheDocument();
  });

  it("disables the convert button until a file is selected", () => {
    renderWithProvider();

    expect(
      screen.getByRole("button", { name: /convert to word/i }),
    ).toBeDisabled();
  });

  it("shows file details and enables conversion once a valid PDF is uploaded", async () => {
    const user = userEvent.setup();
    const { container } = renderWithProvider();
    const input = getFileInput(container);

    await user.upload(input, makeValidPdf("report.pdf"));

    expect(await screen.findByText("report.pdf")).toBeInTheDocument();
    expect(screen.getByText("PDF Selected")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /convert to word/i }),
    ).toBeEnabled();
  });

  it("shows the parsed page count once pdf.js resolves it", async () => {
    const user = userEvent.setup();
    const { container } = renderWithProvider();
    const input = getFileInput(container);

    await user.upload(input, makeValidPdf());

    await waitFor(() =>
      expect(screen.getByText(/3 pages/)).toBeInTheDocument(),
    );
  });

  it("removes the file and disables conversion again when Remove File is clicked", async () => {
    const user = userEvent.setup();
    const { container } = renderWithProvider();
    const input = getFileInput(container);

    await user.upload(input, makeValidPdf());
    expect(await screen.findByText("report.pdf")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /remove file/i }));

    expect(screen.queryByText("report.pdf")).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /convert to word/i }),
    ).toBeDisabled();
  });

  it("switches to the progress view when Convert is clicked", async () => {
    const user = userEvent.setup();

    function ActiveViewProbe() {
      const { activeView } = useConversion();
      return <p>active:{activeView}</p>;
    }

    render(
      <ConversionProvider>
        <UploadScreen />
        <ActiveViewProbe />
      </ConversionProvider>,
    );

    const input = getFileInput(document.body);
    await user.upload(input, makeValidPdf());
    expect(await screen.findByText("report.pdf")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /convert to word/i }));

    expect(screen.getByText("active:progress")).toBeInTheDocument();
  });

  it("renders the preservation feature cards", () => {
    renderWithProvider();

    expect(screen.getByText("What DocLift Preserves")).toBeInTheDocument();
    expect(screen.getByText("Reading Order")).toBeInTheDocument();
    expect(screen.getByText("Embedded Images")).toBeInTheDocument();
  });
});
