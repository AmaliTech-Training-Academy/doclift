export type ConversionStatus = "uploading" | "converting" | "completed" | "failed" | "expired";

export type ConversionSession = {
    jobId: string;
    fileName: string;
    status: ConversionStatus;
    updatedAt: number;
};

const STORAGE_KEY = "doclift-active-conversion";

export function generateJobId(): string {
    if (typeof crypto !== "undefined" && crypto.randomUUID) {
        return `conv_${crypto.randomUUID()}`;
    }
    return `conv_${Date.now()}_${Math.random().toString(36).substring(2, 8)}`;
}

export function saveConversionSession(session: ConversionSession) {
    if (typeof window === "undefined") return;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function getConversionSession(): ConversionSession | null {
    if (typeof window === "undefined") return null;
    const stored = localStorage.getItem(STORAGE_KEY);

    if (!stored) return null;

    try {
        return JSON.parse(stored);
    } catch {
        localStorage.removeItem(STORAGE_KEY);
        return null;
    }
}

export function clearConversionSession() {
    if (typeof window === "undefined") return;
    localStorage.removeItem(STORAGE_KEY);
}