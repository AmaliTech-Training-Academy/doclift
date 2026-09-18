import { describe, expect, it } from "vitest";
import { render, renderHook, screen, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConversionProvider, useConversion } from "./ConversionContext";

function wrapper({ children }: { children: React.ReactNode }) {
  return <ConversionProvider>{children}</ConversionProvider>;
}

describe("useConversion", () => {
  it("throws when used outside of a ConversionProvider", () => {
    expect(() => renderHook(() => useConversion())).toThrow(
      "useConversion must be used within ConversionProvider",
    );
  });

  it("provides sane defaults", () => {
    const { result } = renderHook(() => useConversion(), { wrapper });

    expect(result.current.file).toBeNull();
    expect(result.current.resetKey).toBe(0);
    expect(result.current.activeView).toBe("upload");
  });

  it("sets and clears the file", () => {
    const { result } = renderHook(() => useConversion(), { wrapper });
    const file = new File(["hello"], "hello.pdf", { type: "application/pdf" });

    act(() => result.current.setFile(file));
    expect(result.current.file).toBe(file);

    act(() => result.current.clearFile());
    expect(result.current.file).toBeNull();
  });

  it("updates the active view", () => {
    const { result } = renderHook(() => useConversion(), { wrapper });

    act(() => result.current.setActiveView("progress"));
    expect(result.current.activeView).toBe("progress");
  });

  it("reset clears the file, bumps resetKey, and returns to the upload view", () => {
    const { result } = renderHook(() => useConversion(), { wrapper });
    const file = new File(["hello"], "hello.pdf", { type: "application/pdf" });

    act(() => {
      result.current.setFile(file);
      result.current.setActiveView("progress");
    });
    expect(result.current.resetKey).toBe(0);

    act(() => result.current.reset());

    expect(result.current.file).toBeNull();
    expect(result.current.resetKey).toBe(1);
    expect(result.current.activeView).toBe("upload");
  });

  it("shares state between components under the same provider", async () => {
    const user = userEvent.setup();

    function Reader() {
      const { activeView } = useConversion();
      return <p>view:{activeView}</p>;
    }

    function Writer() {
      const { setActiveView } = useConversion();
      return (
        <button onClick={() => setActiveView("progress")}>go</button>
      );
    }

    render(
      <ConversionProvider>
        <Reader />
        <Writer />
      </ConversionProvider>,
    );

    expect(screen.getByText("view:upload")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "go" }));

    expect(screen.getByText("view:progress")).toBeInTheDocument();
  });
});
