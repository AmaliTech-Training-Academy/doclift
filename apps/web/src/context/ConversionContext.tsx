"use client";

import { createContext, useContext, useState, ReactNode } from "react";
import {
    ConversionSession,
    ConversionStatus,
    generateJobId,
    saveConversionSession,
    clearConversionSession,
} from "@/lib/conversionSession";

export type ActiveView = "upload" | "progress" | "result";

interface ConversionContextValue {
    file: File | null;
    setFile: (file: File | null) => void;
    clearFile: () => void;
    resetKey: number;
    reset: () => void;
    activeView: ActiveView;
    setActiveView: (view: ActiveView) => void;
    session: ConversionSession | null;
    setSession: (session: ConversionSession | null) => void;
    startConversion: (fileOverride?: File | null) => ConversionSession | null;
    updateStatus: (status: ConversionStatus) => void;
}

const ConversionContext = createContext<ConversionContextValue | null>(null);

export function ConversionProvider({ children }: { children: ReactNode }) {
    const [file, setFile] = useState<File | null>(null);
    const [resetKey, setResetKey] = useState(0);
    const [activeView, setActiveView] = useState<ActiveView>("upload");
    const [session, setSession] = useState<ConversionSession | null>(null);

    const clearFile = () => setFile(null);

    const startConversion = (fileOverride?: File | null): ConversionSession | null => {
        const targetFile = fileOverride !== undefined ? fileOverride : file;
        if (!targetFile) return null;

        const fileName = targetFile.name;
        const newSession: ConversionSession = {
            jobId: generateJobId(),
            fileName,
            status: "processing",
            updatedAt: Date.now(),
        };

        saveConversionSession(newSession);
        setSession(newSession);
        setActiveView("progress");
        return newSession;
    };

    const updateStatus = (status: ConversionStatus) => {
        if (!session) return;
        const updatedSession: ConversionSession = {
            ...session,
            status,
            updatedAt: Date.now(),
        };
        saveConversionSession(updatedSession);
        setSession(updatedSession);
    };

    const reset = () => {
        setFile(null);
        setSession(null);
        clearConversionSession();
        setResetKey((k) => k + 1);
        setActiveView("upload");
    };

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
                session,
                setSession,
                startConversion,
                updateStatus,
            }}
        >
            {children}
        </ConversionContext.Provider>
    );
}

export function useConversion() {
    const ctx = useContext(ConversionContext);
    if (!ctx) throw new Error("useConversion must be used within ConversionProvider");
    return ctx;
}
