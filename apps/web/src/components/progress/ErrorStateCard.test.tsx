import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ErrorStateCard from "./ErrorStateCard";
import { ConversionProvider, useConversion } from "@/context/ConversionContext";

function renderWithProvider(ui: React.ReactElement) {
  return render(<ConversionProvider>{ui}</ConversionProvider>);
}

describe("ErrorStateCard", () => {
  it("renders the error heading, message, and a retry button that triggers reset", async () => {
    const user = userEvent.setup();

    function ViewProbe() {
      const { activeView, setActiveView } = useConversion();
      return (
        <div>
          <button onClick={() => setActiveView("progress")}>set progress</button>
          <p>view:{activeView}</p>
          <ErrorStateCard />
        </div>
      );
    }

    renderWithProvider(<ViewProbe />);

    expect(screen.getByRole("heading", { name: "Error" })).toBeInTheDocument();
    expect(
      screen.getByText("An error has occurred. Your file conversion failed."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Click retry to try the conversion once more."),
    ).toBeInTheDocument();

    const retryBtn = screen.getByRole("button", { name: "Retry" });
    expect(retryBtn).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "set progress" }));
    expect(screen.getByText("view:progress")).toBeInTheDocument();

    await user.click(retryBtn);
    expect(screen.getByText("view:upload")).toBeInTheDocument();
  });

  it("renders custom error message and triggers custom reset handler when provided", async () => {
    const user = userEvent.setup();
    const mockReset = vi.fn();

    renderWithProvider(
      <ErrorStateCard error="Custom error message" reset={mockReset} />
    );

    expect(screen.getByText("Custom error message")).toBeInTheDocument();

    const retryBtn = screen.getByRole("button", { name: "Retry" });
    await user.click(retryBtn);

    expect(mockReset).toHaveBeenCalledTimes(1);
  });
});