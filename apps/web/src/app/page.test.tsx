import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { useEffect } from "react";
import Home from "./page";
import { ConversionProvider, useConversion } from "@/context/ConversionContext";

vi.mock("@/components/upload/UploadScreen", () => ({
  default: () => <div data-testid="upload-screen" />,
}));

vi.mock("@/components/progress/ProgressScreen", () => ({
  default: () => <div data-testid="progress-screen" />,
}));

describe("Home", () => {
  it("renders the upload screen by default", () => {
    render(
      <ConversionProvider>
        <Home />
      </ConversionProvider>,
    );

    expect(screen.getByTestId("upload-screen")).toBeInTheDocument();
    expect(screen.queryByTestId("progress-screen")).not.toBeInTheDocument();
  });

  it("renders the progress screen once the active view switches to progress", () => {
    function SwitchToProgress() {
      const { setActiveView } = useConversion();
      useEffect(() => setActiveView("progress"), [setActiveView]);
      return <Home />;
    }

    render(
      <ConversionProvider>
        <SwitchToProgress />
      </ConversionProvider>,
    );

    expect(screen.getByTestId("progress-screen")).toBeInTheDocument();
    expect(screen.queryByTestId("upload-screen")).not.toBeInTheDocument();
  });
});
