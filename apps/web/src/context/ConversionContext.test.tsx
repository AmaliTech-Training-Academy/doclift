import { describe, expect, it } from "vitest";
import { render, renderHook, screen, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConversionProvider, useConversion } from "./ConversionContext";
import type { ConversionSession } from "@/lib/conversionSession";

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

  it("returns null when startConversion is called without a selected file", () => {
    const { result } = renderHook(() => useConversion(), { wrapper });

    let session: ConversionSession | null = null;
    act(() => {
      session = result.current.startConversion();
    });

    expect(session).toBeNull();
    expect(result.current.session).toBeNull();
    expect(result.current.activeView).toBe("upload");
  });

  it("returns a session when startConversion is called with a file or fileOverride", () => {
    const { result } = renderHook(() => useConversion(), { wrapper });
    const file = new File(["pdf"], "doc.pdf", { type: "application/pdf" });

    let session: ConversionSession | null = null;
    act(() => {
      session = result.current.startConversion(file);
    });

    expect(session).not.toBeNull();
    expect((session as ConversionSession | null)?.fileName).toBe("doc.pdf");
    expect(result.current.activeView).toBe("progress");
  });

  it("resetKeepFile clears session and returns to upload view while retaining the uploaded file", () => {
    const { result } = renderHook(() => useConversion(), { wrapper });
    const file = new File(["hello"], "hello.pdf", { type: "application/pdf" });

    act(() => {
      result.current.startConversion(file);
    });

    act(() => {
      result.current.updateStatus("failed");
    });

    expect(result.current.session?.status).toBe("failed");

    act(() => result.current.resetKeepFile());

    expect(result.current.session).toBeNull();
    expect(result.current.activeView).toBe("upload");
  });
});
