import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Input } from "./Input";

describe("Input", () => {
  it("renders with the given type and forwards standard input props", () => {
    render(<Input type="email" placeholder="you@example.com" />);
    const input = screen.getByPlaceholderText("you@example.com");

    expect(input).toHaveAttribute("type", "email");
  });

  it("accepts user input", async () => {
    const user = userEvent.setup();
    render(<Input aria-label="name" />);
    const input = screen.getByLabelText("name");

    await user.type(input, "hello");

    expect(input).toHaveValue("hello");
  });

  it("calls onChange handlers passed through props", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Input aria-label="name" onChange={onChange} />);

    await user.type(screen.getByLabelText("name"), "a");

    expect(onChange).toHaveBeenCalled();
  });
});
