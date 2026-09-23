import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ErrorStateCard from "./ErrorStateCard";

const mockLocationReload = () => {
  const reload = vi.fn();
  const originalLocation = window.location;
  Object.defineProperty(window, "location", {
    configurable: true,
    value: { ...originalLocation, reload },
  });
  return reload;
};

describe("ErrorStateCard", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders the error heading, message, and a retry button", () => {
    render(<ErrorStateCard />);

    expect(screen.getByRole("heading", { name: "Error" })).toBeInTheDocument();
    expect(
      screen.getByText("An error has occurred. Please try again later."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("calls reset and reloads the page when the retry button is clicked", async () => {
    const user = userEvent.setup();
    const reset = vi.fn();
    const reload = mockLocationReload();
    render(<ErrorStateCard reset={reset} />);

    await user.click(screen.getByRole("button", { name: "Retry" }));

    expect(reset).toHaveBeenCalledTimes(1);
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("reloads the page when the retry button is clicked without a reset callback", async () => {
    const user = userEvent.setup();
    const reload = mockLocationReload();
    render(<ErrorStateCard />);

    await user.click(screen.getByRole("button", { name: "Retry" }));

    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("logs an Error object to the console when provided", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    const error = new Error("boom");

    render(<ErrorStateCard error={error} />);

    expect(consoleError).toHaveBeenCalledWith(error);
  });

  it("logs a string error to the console when provided", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});

    render(<ErrorStateCard error="something went wrong" />);

    expect(consoleError).toHaveBeenCalledWith("something went wrong");
  });

  it("does not log to the console when no error is provided", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});

    render(<ErrorStateCard />);

    expect(consoleError).not.toHaveBeenCalled();
  });
});
