"use client";

import { createContext, useContext, useState, useEffect, ReactNode } from "react";
import {
    ConversionSession,
    ConversionStatus,
    generateJobId,
    saveConversionSession,
    clearConversionSession,
} from "@/lib/conversionSession";
import { saveDraftFile, getDraftFile, clearDraftFile } from "@/lib/fileStorage";
import AbandonSessionModal from "@/components/ui/AbandonSessionModal";

export type ActiveView = "upload" | "progress" | "result";

interface ConversionContextValue {
    file: File | null;
    setFile: (file: File | null) => void;
    clearFile: () => void;
    resetKey: number;
    reset: () => void;
    resetKeepFile: () => void;
    requestReset: () => void;
    isAbandonModalOpen: boolean;
    openAbandonModal: () => void;
    closeAbandonModal: () => void;
    activeView: ActiveView;
    setActiveView: (view: ActiveView) => void;
    session: ConversionSession | null;
    setSession: (session: ConversionSession | null) => void;
    startConversion: (fileOverride?: File | null) => ConversionSession | null;
    updateStatus: (status: ConversionStatus) => void;
}

const ConversionContext = createContext<ConversionContextValue | null>(null);

export function ConversionProvider({ children }: { children: ReactNode }) {
    const [file, setFileState] = useState<File | null>(null);
    const [resetKey, setResetKey] = useState(0);
    const [activeView, setActiveView] = useState<ActiveView>("upload");
    const [session, setSession] = useState<ConversionSession | null>(null);
    const [isAbandonModalOpen, setIsAbandonModalOpen] = useState(false);

    useEffect(() => {
        let isMounted = true;
        getDraftFile().then((savedFile) => {
            if (isMounted && savedFile) {
                setFileState(savedFile);
            }
        });
        return () => {
            isMounted = false;
        };
    }, []);

    const setFile = (newFile: File | null) => {
        setFileState(newFile);
        if (newFile) {
            saveDraftFile(newFile);
        } else {
            clearDraftFile();
        }
    };

    const clearFile = () => {
        setFileState(null);
        clearDraftFile();
    };

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
        setSession((prevSession) => {
            if (!prevSession) return null;
            const updatedSession: ConversionSession = {
                ...prevSession,
                status,
                updatedAt: Date.now(),
            };
            saveConversionSession(updatedSession);
            return updatedSession;
        });
    };

    const reset = () => {
        setFile(null);
        setSession(null);
        clearConversionSession();
        setResetKey((k) => k + 1);
        setActiveView("upload");
        setIsAbandonModalOpen(false);
    };

    const resetKeepFile = () => {
        setSession(null);
        clearConversionSession();
        setResetKey((k) => k + 1);
        setActiveView("upload");
        setIsAbandonModalOpen(false);
    };

    const requestReset = () => {
        if (session) {
            setIsAbandonModalOpen(true);
        } else {
            reset();
        }
    };

    const handleConfirmAbandon = () => {
        if (session?.status === "failed") {
            resetKeepFile();
        } else {
            reset();
        }
    };

    return (
        <ConversionContext.Provider
            value={{
                file,
                setFile,
                clearFile,
                resetKey,
                reset,
                resetKeepFile,
                requestReset,
                isAbandonModalOpen,
                openAbandonModal: () => setIsAbandonModalOpen(true),
                closeAbandonModal: () => setIsAbandonModalOpen(false),
                activeView,
                setActiveView,
                session,
                setSession,
                startConversion,
                updateStatus,
            }}
        >
            {children}
            <AbandonSessionModal
                isOpen={isAbandonModalOpen}
                onClose={() => setIsAbandonModalOpen(false)}
                onConfirm={handleConfirmAbandon}
                session={session}
            />
        </ConversionContext.Provider>
    );
}

export function useConversion() {
  const ctx = useContext(ConversionContext);
  if (!ctx) throw new Error("useConversion must be used within ConversionProvider");
  return ctx;
}
