"use client";

import { createContext, useContext, useState, ReactNode } from "react";

interface ConversionContextValue {
    file: File | null;
    setFile: (file: File | null) => void;
    clearFile: () => void;
    resetKey: number;
    reset: () => void;
}

const ConversionContext = createContext<ConversionContextValue | null>(null);

export function ConversionProvider({ children }: { children: ReactNode }) {
    const [file, setFile] = useState<File | null>(null);
    const [resetKey, setResetKey] = useState(0);

    const clearFile = () => setFile(null);
    const reset = () => {
        setFile(null);
        setResetKey((k) => k + 1);
    };

    return (
        <ConversionContext.Provider value={{ file, setFile, clearFile, resetKey, reset }}>
            {children}
        </ConversionContext.Provider>
    );
}

export function useConversion() {
    const ctx = useContext(ConversionContext);
    if (!ctx) throw new Error("useConversion must be used within ConversionProvider");
    return ctx;
}
