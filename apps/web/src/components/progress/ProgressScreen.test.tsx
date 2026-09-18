import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { useEffect } from "react";
import ProgressScreen from "./ProgressScreen";
import { ConversionProvider, useConversion } from "@/context/ConversionContext";

vi.mock("./HeaderBar", () => ({
  default: ({ file }: { file: File | null }) => (
    <div data-testid="header-bar">{file ? file.name : "no file"}</div>
  ),
}));

vi.mock("./ProgressCard", () => ({
  default: () => <div data-testid="progress-card" />,
}));

describe("ProgressScreen", () => {
  it("renders the header bar and the progress card", () => {
    render(
      <ConversionProvider>
        <ProgressScreen />
      </ConversionProvider>,
    );

    expect(screen.getByTestId("header-bar")).toBeInTheDocument();
    expect(screen.getByTestId("progress-card")).toBeInTheDocument();
  });

  it("passes the file from context down to the header bar", () => {
    const file = new File(["%PDF-1.4"], "contract.pdf", {
      type: "application/pdf",
    });

    function SetFileThenRender() {
      const { setFile } = useConversion();
      useEffect(() => setFile(file), [setFile]);
      return <ProgressScreen />;
    }

    render(
      <ConversionProvider>
        <SetFileThenRender />
      </ConversionProvider>,
    );

    expect(screen.getByTestId("header-bar")).toHaveTextContent("contract.pdf");
  });
});
