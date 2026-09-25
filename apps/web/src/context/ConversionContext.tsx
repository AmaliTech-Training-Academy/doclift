"use client";

import { createContext, useContext, useState, useEffect, ReactNode } from "react";
import { ConversionSession, ConversionStatus, saveConversionSession, clearConversionSession } from "@/lib/conversionSession";
import { saveDraftFile, getDraftFile, clearDraftFile } from "@/lib/fileStorage";
import { uploadFile } from "@/lib/uploadApi";
import AbandonSessionModal from "@/components/ui/AbandonSessionModal";
import { toast } from "sonner";

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
    isUploading: boolean;
    isConverting: boolean;
    startConversion: (fileOverride?: File | null) => Promise<ConversionSession | null>;
    updateStatus: (status: ConversionStatus, durationOverride?: number) => void;
}

const ConversionContext = createContext<ConversionContextValue | null>(null);

export function ConversionProvider({ children }: { children: ReactNode }) {
    const [file, setFileState] = useState<File | null>(null);
    const [resetKey, setResetKey] = useState(0);
    const [activeView, setActiveView] = useState<ActiveView>("upload");
    const [session, setSession] = useState<ConversionSession | null>(null);
    const [isUploading, setIsUploading] = useState(false);
    const [isAbandonModalOpen, setIsAbandonModalOpen] = useState(false);

    const isConverting = Boolean(
        session && (session.status === "processing" || session.status === "queued")
    );

    useEffect(() => {
        let isMounted = true;
        getDraftFile().then((savedFile) => {
            if (isMounted && savedFile) {
                setFileState((currentFile) => currentFile ?? savedFile);
            }
        });
        return () => {
            isMounted = false;
        };
    }, []);

    const setFile = (newFile: File | null) => {
        setFileState(newFile);
        if (newFile) {
            saveDraftFile(newFile).then((saved) => {
                if (!saved) {
                    if (typeof toast?.warning === "function") {
                        toast.warning("Could not save file draft for page refresh recovery.");
                    } else if (typeof toast?.error === "function") {
                        toast.error("Could not save file draft for page refresh recovery.");
                    }
                }
            });
        } else {
            clearDraftFile();
        }
    };

    const clearFile = () => {
        setFileState(null);
        clearDraftFile();
    };

    const startConversion = async (fileOverride?: File | null): Promise<ConversionSession | null> => {
        const targetFile = fileOverride !== undefined ? fileOverride : file;
        if (!targetFile || isUploading || isConverting) return null;

        setIsUploading(true);

        try {
            const res = await uploadFile(targetFile);
            const now = Date.now();
            const realJobId = String(res.jobId);
            const newSession: ConversionSession = {
                jobId: realJobId,
                fileName: targetFile.name,
                status: "processing",
                updatedAt: now,
                createdAt: now,
                fileSize: targetFile.size,
            };

            saveConversionSession(newSession);
            setSession(newSession);
            setActiveView("progress");
            setIsUploading(false);
            return newSession;
        } catch (err: unknown) {
            const errorMessage = err instanceof Error ? err.message : "Upload failed";
            if (typeof toast?.error === "function") {
                toast.error(errorMessage);
            }
            setIsUploading(false);
            return null;
        }
    };

    const updateStatus = (status: ConversionStatus, durationOverride?: number) => {
        setSession((prevSession) => {
            if (!prevSession) return null;
            const now = Date.now();
            const createdAt = prevSession.createdAt || prevSession.updatedAt;
            const calculatedDuration = Math.max(1, Math.round((now - createdAt) / 1000));
            const durationSeconds =
                durationOverride !== undefined
                    ? durationOverride
                    : status === "done"
                    ? (prevSession.durationSeconds ?? calculatedDuration)
                    : prevSession.durationSeconds;

            const updatedSession: ConversionSession = {
                ...prevSession,
                status,
                updatedAt: now,
                durationSeconds,
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
                isUploading,
                isConverting,
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
