const DB_NAME = "doclift-file-db";
const STORE_NAME = "draft-files";
const FILE_KEY = "current-uploaded-file";

function getDB(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
        if (typeof window === "undefined" || !window.indexedDB) {
            return reject(new Error("IndexedDB not available"));
        }
        const request = indexedDB.open(DB_NAME, 1);
        request.onupgradeneeded = () => {
            const db = request.result;
            if (!db.objectStoreNames.contains(STORE_NAME)) {
                db.createObjectStore(STORE_NAME);
            }
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error || new Error("IndexedDB open error"));
    });
}

export async function saveDraftFile(file: File): Promise<void> {
    try {
        const db = await getDB();
        const tx = db.transaction(STORE_NAME, "readwrite");
        const store = tx.objectStore(STORE_NAME);
        store.put(file, FILE_KEY);
        return new Promise((resolve, reject) => {
            tx.oncomplete = () => resolve();
            tx.onerror = () => reject(tx.error || new Error("Save draft file error"));
        });
    } catch {
        // Silently catch in environments without IndexedDB support
    }
}

export async function getDraftFile(): Promise<File | null> {
    try {
        const db = await getDB();
        const tx = db.transaction(STORE_NAME, "readonly");
        const store = tx.objectStore(STORE_NAME);
        const request = store.get(FILE_KEY);
        return new Promise((resolve, reject) => {
            request.onsuccess = () => {
                const res = request.result;
                if (res instanceof File) {
                    resolve(res);
                } else if (res && typeof res === "object" && "name" in res) {
                    try {
                        const blob = res as Blob & { name?: string; lastModified?: number };
                        const restoredFile = new File([blob], blob.name || "uploaded.pdf", {
                            type: blob.type || "application/pdf",
                            lastModified: blob.lastModified || Date.now(),
                        });
                        resolve(restoredFile);
                    } catch {
                        resolve(null);
                    }
                } else {
                    resolve(null);
                }
            };
            request.onerror = () => reject(request.error || new Error("Get draft file error"));
        });
    } catch {
        return null;
    }
}

export async function clearDraftFile(): Promise<void> {
    try {
        const db = await getDB();
        const tx = db.transaction(STORE_NAME, "readwrite");
        const store = tx.objectStore(STORE_NAME);
        store.delete(FILE_KEY);
        return new Promise((resolve, reject) => {
            tx.oncomplete = () => resolve();
            tx.onerror = () => reject(tx.error || new Error("Clear draft file error"));
        });
    } catch {
        // Silently catch in environments without IndexedDB support
    }
}
