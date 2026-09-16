"use client";

import { createContext, useContext, useState, ReactNode } from "react";

interface ConversionContextValue {
    resetKey: number;
    reset: () => void;
}

const ConversionContext = createContext<ConversionContextValue | null>(null);

export function ConversionProvider({ children }: { children: ReactNode }) {
    const [resetKey, setResetKey] = useState(0);

    const reset = () => setResetKey((k) => k + 1);

    return (
        <ConversionContext.Provider value={{ resetKey, reset }}>
            {children}
        </ConversionContext.Provider>
    );
}

export function useConversion() {
    const ctx = useContext(ConversionContext);
    if (!ctx) throw new Error("useConversion must be used within ConversionProvider");
    return ctx;
}
