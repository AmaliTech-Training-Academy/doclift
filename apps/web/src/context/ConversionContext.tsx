"use client";

import { createContext, useContext, useState, useEffect, useRef, useCallback, ReactNode } from "react";
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
    const abortControllerRef = useRef<AbortController | null>(null);
    const isUploadingRef = useRef(false);

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

    const setFile = useCallback((newFile: File | null) => {
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
    }, []);

    const clearFile = useCallback(() => {
        setFileState(null);
        clearDraftFile();
    }, []);

    const startConversion = async (fileOverride?: File | null): Promise<ConversionSession | null> => {
        const targetFile = fileOverride !== undefined ? fileOverride : file;
        if (!targetFile || isUploadingRef.current || isConverting) return null;

        isUploadingRef.current = true;
        const controller = new AbortController();
        abortControllerRef.current = controller;
        setIsUploading(true);

        try {
            const res = await uploadFile(targetFile, controller.signal);

            // If reset() was called while the request was in flight, bail out.
            if (controller.signal.aborted) return null;

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

            try {
                saveConversionSession(newSession);
            } catch (storageErr) {
                console.warn("Could not persist conversion session to storage:", storageErr);
                toast.warning("Progress may be lost if the page is refreshed.");
            }

            setSession(newSession);
            setActiveView("progress");
            return newSession;
        } catch (err: unknown) {
            if (err instanceof Error && err.name === "AbortError") return null;

            const errorMessage = err instanceof Error ? err.message : "Upload failed";
            if (typeof toast?.error === "function") {
                toast.error(errorMessage);
            }
            return null;
        } finally {
            isUploadingRef.current = false;
            abortControllerRef.current = null;
            setIsUploading(false);
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
        // Cancel any in-flight upload before clearing state.
        abortControllerRef.current?.abort();
        abortControllerRef.current = null;
        isUploadingRef.current = false;
        setIsUploading(false);
        setFile(null);
        setSession(null);
        clearConversionSession();
        setResetKey((k) => k + 1);
        setActiveView("upload");
        setIsAbandonModalOpen(false);
    };

    const resetKeepFile = () => {
        isUploadingRef.current = false;
        setIsUploading(false);
        setSession(null);
        clearConversionSession();
        setResetKey((k) => k + 1);
        setActiveView("upload");
        setIsAbandonModalOpen(false);
    };

    const requestReset = () => {
        if (session || isUploading) {
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

    const openAbandonModal = useCallback(() => setIsAbandonModalOpen(true), []);
    const closeAbandonModal = useCallback(() => setIsAbandonModalOpen(false), []);

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
                openAbandonModal,
                closeAbandonModal,
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
