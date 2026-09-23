import { describe, expect, it } from "vitest";
import { saveDraftFile, getDraftFile, clearDraftFile } from "./fileStorage";

describe("fileStorage", () => {
    it("handles missing IndexedDB gracefully without throwing", async () => {
        const testFile = new File(["dummy content"], "test.pdf", { type: "application/pdf" });

        await expect(saveDraftFile(testFile)).resolves.not.toThrow();
        await expect(getDraftFile()).resolves.toBeNull();
        await expect(clearDraftFile()).resolves.not.toThrow();
    });
});
