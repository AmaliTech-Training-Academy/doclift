import { describe, expect, it } from "vitest";
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
      screen.getByText("An error has occurred. Your File conversion failed."),
    ).toBeInTheDocument();
    expect(screen.getByText("Please try again later.")).toBeInTheDocument();

    const retryBtn = screen.getByRole("button", { name: "Retry" });
    expect(retryBtn).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "set progress" }));
    expect(screen.getByText("view:progress")).toBeInTheDocument();

    await user.click(retryBtn);
    expect(screen.getByText("view:upload")).toBeInTheDocument();
  });
});
