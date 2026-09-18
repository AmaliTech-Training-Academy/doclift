"use client";

import {
  createContext,
  useContext,
  useState,
  ReactNode,
  useEffect,
} from "react";

export type ActiveView = "upload" | "progress" | "result";

interface ConversionContextValue {
  file: File | null;
  setFile: (file: File | null) => void;
  clearFile: () => void;
  resetKey: number;
  reset: () => void;
  activeView: ActiveView;
  setActiveView: (view: ActiveView) => void;
}

const ConversionContext = createContext<ConversionContextValue | null>(null);

export function ConversionProvider({ children }: { children: ReactNode }) {
  const [file, setFile] = useState<File | null>(null);
  const [resetKey, setResetKey] = useState(0);
  const [activeView, setActiveViewState] = useState<ActiveView>("upload");

  const setActiveView = (view: ActiveView) => {
    setActiveViewState(view);
    localStorage.setItem("activeView", view);
  };

  const clearFile = () => setFile(null);
  const reset = () => {
    setFile(null);
    setResetKey((k) => k + 1);
    setActiveView("upload");
  };

  useEffect(() => {
    const savedScreen = localStorage.getItem("activeView") as ActiveView | null;
    if (savedScreen) {
      setActiveViewState(savedScreen);
    }
  }, []);

  return (
    <ConversionContext.Provider
      value={{
        file,
        setFile,
        clearFile,
        resetKey,
        reset,
        activeView,
        setActiveView,
      }}
    >
      {children}
    </ConversionContext.Provider>
  );
}

export function useConversion() {
  const ctx = useContext(ConversionContext);
  if (!ctx)
    throw new Error("useConversion must be used within ConversionProvider");
  return ctx;
}
