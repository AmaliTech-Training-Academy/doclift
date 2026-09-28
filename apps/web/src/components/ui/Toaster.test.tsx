import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Toaster } from "./Toaster";

vi.mock("next-themes", () => ({
  useTheme: () => ({ theme: "light" }),
}));

describe("Toaster", () => {
  it("renders without crashing", () => {
    const { container } = render(<Toaster />);
    expect(container).toBeDefined();
  });
});
