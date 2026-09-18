import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import ErrorStateCard from "./ErrorStateCard";

describe("ErrorStateCard", () => {
  it("renders the error heading, message, and a retry button", () => {
    render(<ErrorStateCard />);

    expect(screen.getByRole("heading", { name: "Error" })).toBeInTheDocument();
    expect(
      screen.getByText("An error has occurred. Please try again later."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });
});
