"use client";

import ErrorStateCard from "@/components/progress/ErrorStateCard";
import UploadScreen from "@/components/upload/UploadScreen";



export default function DevErrorPreviewPage() {
  return (
    <main className="min-h-dvh flex flex-col">
    <div className="flex justify-center py-10">
      <ErrorStateCard
        error="Sample error for visual preview"
        reset={() => <UploadScreen />}
      />
    </div>
    </main>
  );
}
