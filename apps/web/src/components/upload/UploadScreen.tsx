"use client";

import { useRef, useState, useEffect } from "react";
import { GlobalWorkerOptions, getDocument } from "pdfjs-dist";
import DropZone, { DropZoneHandle } from "@/components/upload/DropZone";
import { FileText, ArrowLeftRight, Trash, CheckCircle } from "lucide-react";
import Button from "@/components/ui/Button";
import { useConversion } from "@/context/ConversionContext";

import PreservationCard from "@/components/upload/PreservationCard";
import { preservationData } from "@/data/preservationData";

GlobalWorkerOptions.workerSrc = new URL(
  "pdfjs-dist/build/pdf.worker.min.mjs",
  import.meta.url,
).toString();

function formatFileSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

async function getPdfPageCount(file: File): Promise<number | null> {
  try {
    const data = new Uint8Array(await file.arrayBuffer());
    const pdf = await getDocument({ data }).promise;
    return pdf.numPages;
  } catch (error) {
    console.error("Failed to read PDF page count:", error);
    return null;
  }
}

export default function UploadScreen() {
  const {
    file: uploadedFile,
    setFile: setUploadedFile,
    clearFile,
    setActiveView,
  } = useConversion();
  const dropZoneRef = useRef<DropZoneHandle>(null);
  const [pageCount, setPageCount] = useState<number | null>(null);

  useEffect(() => {
    if (!uploadedFile) return;

    let isMounted = true;
    getPdfPageCount(uploadedFile).then((count) => {
      if (isMounted) setPageCount(count);
    });
    return () => {
      isMounted = false;
    };
  }, [uploadedFile]);

  const handleFileDrop = (file: File) => {
    setPageCount(null);
    setUploadedFile(file);
  };

  const handleRemoveFile = () => {
    setPageCount(null);
    clearFile();
    dropZoneRef.current?.clearFile();
  };

  return (
    <div className="flex-1 space-y-4 p-4">
      <div className="bg-white w-full mx-auto max-w-3xl flex flex-col space-y-4 p-4 mb-10 rounded-xl">
        <div className="mt-8 max-w-2xl text-center mx-auto space-y-4">
          <h1 className="text-4xl font-bold">
            TRANSFORM YOUR PDF INTO EDITABLE WORD DOCUMENT
          </h1>
          <p>
            Convert digital PDFs into editable Word documents while preserving
            text, structure, formatting, images, tables, and reading order.
          </p>
        </div>
        <DropZone ref={dropZoneRef} onDrop={handleFileDrop}>
          <div>
            <div className="p-4 bg-blue-200 rounded-2xl">
              <FileText className="size-15 text-blue-600" />
            </div>
            <div className="relative w-fit left-1/2 top-1/2 -translate-y-1/2 translate-x-6 bg-blue-600 rounded-full p-2">
              <ArrowLeftRight className="size-4 text-white" />
            </div>
          </div>
          <h1 className="text-center text-2xl">Drag & drop your PDF here</h1>
        </DropZone>
        <div className="w-full flex sm:flex-row flex-col justify-around p-2 items-center bg-blue-100 rounded-xl max-w-4xl mx-auto">
          <div>
            {" "}
            <p>
              Supported format:{" "}
              <span className="font-semibold">PDF only (.pdf)</span>
            </p>
          </div>
          <div className="sm:inline hidden rounded-full bg-black p-1 w-fit h-fit"></div>
          <div>
            <p>
              Limit: <span className="font-semibold">Up to 50 MB</span>
            </p>
          </div>
          <div className="sm:inline hidden rounded-full bg-black p-1 w-fit h-fit"></div>
          <div>
            <p>
              Source: <span className="font-semibold">Digital text layer</span>
            </p>
          </div>
        </div>

        {uploadedFile && (
          <div className="w-full flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 p-4 border border-gray-200 rounded-xl">
            <div className="flex flex-row items-center space-x-3 sm:space-x-4 min-w-0 w-full sm:w-auto flex-1">
              <FileText className="size-10 sm:size-12 text-blue-600 shrink-0" />
              <div className="flex-1 flex flex-col space-y-1 sm:space-y-2 min-w-0">
                <p className="text-base sm:text-xl font-semibold truncate">
                  {uploadedFile.name}
                </p>
                <div className="flex flex-wrap items-center gap-2">
                  <div className="text-xs sm:text-sm bg-blue-100 rounded-lg w-fit px-2 py-1 text-blue-600">
                    <span>PDF selected</span>
                  </div>
                  <p className="text-xs sm:text-sm text-gray-500">
                    {formatFileSize(uploadedFile.size)}
                    {pageCount !== null &&
                      ` • ${pageCount} ${pageCount === 1 ? "page" : "pages"}`}
                  </p>
                </div>
              </div>
            </div>
            <Button
              variant="danger"
              onClick={handleRemoveFile}
              className="w-full sm:w-auto justify-center"
            >
              <Trash className="size-4" />
              <span>Remove File</span>
            </Button>
          </div>
        )}

                <Button disabled={!uploadedFile} onClick={() => setActiveView('progress')}>
                    <p className="text-xl">Convert to Word (.docx)</p>
                </Button>
                
            </div>
            {/* Bottom Section*/}
            <div className="w-full flex flex-col space-y-4 sm:px-8 px-2 py-4 bg-blue-100 rounded-xl">
                <div className="flex sm:flex-row flex-col justify-between gap-2">
                    <div className="flex flex-col">
                        <h1 className="text-2xl">What DocLift Preserves</h1>
                        <p className="text-sm">Unlike generic OCR or naive converters that dump arbitrary text frames, DocLift reconstructs the logical semantic tree of your document.</p>
                    </div>
                    <div className="w-fit h-fit inline-flex items-center gap-2 bg-white p-2 rounded-lg">
                        <CheckCircle className="size-4 text-green-500"/>
                        <p className="text-sm">99.8% Word Style Parity</p>
                    </div>
                </div>
            {/* Bottom Section Cards*/}
                <div className="grid sm:grid-cols-3 grid-cols-1 gap-4">
                    {preservationData.map((item) => (
                        <PreservationCard
                            key={item.id}
                            item={item}
                        />
                    ))}
                </div>
            </div>
        </div>
    );
}
