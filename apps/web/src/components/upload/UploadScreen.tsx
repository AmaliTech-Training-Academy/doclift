"use client";

import { useRef, useState, useEffect } from "react";
import { GlobalWorkerOptions, getDocument } from "pdfjs-dist";
import DropZone, { DropZoneHandle } from "@/components/upload/DropZone";
import { FileText, ArrowLeftRight, Trash, CheckCircle, ArrowRight } from "lucide-react";
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
    const { file: uploadedFile, setFile: setUploadedFile, clearFile } = useConversion();
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
            <div className="bg-white w-full mx-auto max-w-3xl flex flex-col space-y-6 p-6 sm:p-8 mb-10 rounded-2xl border border-blue-100 shadow-xs">
                <div className="mt-2 max-w-2xl text-center mx-auto space-y-3">
                    <h1 className="text-2xl sm:text-3xl lg:text-4xl font-bold tracking-tight text-gray-900 font-heading">
                        Transform Your PDF into Editable Word Documents
                    </h1>
                    <p className="text-sm sm:text-base text-gray-600 leading-relaxed max-w-xl mx-auto">
                        Convert digital PDFs into editable Word documents while preserving text, structure, formatting, images, tables, and reading order.
                    </p>
                </div>

                <DropZone ref={dropZoneRef} onDrop={handleFileDrop}>
                    <div className="relative mb-3">
                        <div className="size-16 sm:size-20 rounded-2xl bg-blue-100/70 border border-blue-200/80 flex items-center justify-center text-blue-600 shadow-2xs group-hover:scale-105 group-hover:bg-blue-600 group-hover:text-white transition-all duration-300">
                            <FileText className="size-8 sm:size-10" />
                        </div>
                        <div className="absolute -bottom-1 -right-1 size-7 rounded-full bg-blue-600 text-white flex items-center justify-center shadow-xs border-2 border-white group-hover:bg-blue-700 transition-colors">
                            <ArrowLeftRight className="size-3" />
                        </div>
                    </div>
                    <h3 className="text-lg sm:text-xl font-semibold text-gray-900 font-sans group-hover:text-blue-600 transition-colors">
                        Drag & drop your PDF here
                    </h3>
                    <p className="text-xs sm:text-sm text-gray-500 mt-1 max-w-sm">
                        Support for digital text layer PDFs with full layout preservation
                    </p>
                </DropZone>

                <div className="w-full flex flex-wrap items-center justify-center gap-x-5 gap-y-2 py-2.5 px-4 bg-slate-50 border border-slate-200/70 rounded-xl text-xs sm:text-sm text-gray-600">
                    <div className="flex items-center gap-2">
                        <span className="size-1.5 rounded-full bg-blue-600 shrink-0"></span>
                        <span>Supported: <strong className="font-semibold text-gray-800">PDF only (.pdf)</strong></span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="size-1.5 rounded-full bg-blue-600 shrink-0"></span>
                        <span>Limit: <strong className="font-semibold text-gray-800">Up to 50 MB</strong></span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="size-1.5 rounded-full bg-blue-600 shrink-0"></span>
                        <span>Source: <strong className="font-semibold text-gray-800">Digital text layer</strong></span>
                    </div>
                </div>

                {uploadedFile && (
                    <div className="w-full flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 p-4 border border-blue-200 rounded-xl">
                        <div className="flex flex-row items-center space-x-3 sm:space-x-4 min-w-0 w-full sm:w-auto flex-1">
                            <div className="size-12 rounded-xl bg-blue-100 text-blue-600 flex items-center justify-center shrink-0">
                                <FileText className="size-6 text-blue-600" />
                            </div>
                            <div className="flex-1 flex flex-col space-y-1 min-w-0">
                                <p className="text-base sm:text-lg font-semibold truncate">{uploadedFile.name}</p>
                                <div className="flex flex-wrap items-center gap-2">
                                    <span className="text-xs bg-blue-100 font-medium rounded-md px-2 py-1 text-blue-600">
                                        PDF Selected
                                    </span>
                                    <p className="text-xs sm:text-sm text-gray-500">
                                        {formatFileSize(uploadedFile.size)}
                                        {pageCount !== null && ` • ${pageCount} ${pageCount === 1 ? "page" : "pages"}`}
                                    </p>
                                </div>
                            </div>
                        </div>
                        <Button variant="danger" size="sm" onClick={handleRemoveFile} className="w-full sm:w-auto justify-center">
                            <Trash className="size-4"/>
                            <span>Remove File</span>
                        </Button>
                    </div>
                )}

                <Button size="lg" disabled={!uploadedFile} className="w-full">
                    <span>Convert to Word (.docx)</span>
                    <ArrowRight className="size-4" />
                </Button>
            </div>
            {/* Bottom Section: What DocLift Preserves */}
            <section
                aria-labelledby="preserves-heading"
                className="w-full mx-auto flex flex-col space-y-6 sm:p-8 p-5 bg-white border border-blue-100 rounded-2xl"
            >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <div className="flex flex-col space-y-1">
                        <h2
                            id="preserves-heading"
                            className="text-2xl sm:text-3xl font-bold tracking-tight text-gray-900 font-heading"
                        >
                            What DocLift Preserves
                        </h2>
                        <p className="text-sm sm:text-base text-gray-600 max-w-2xl leading-relaxed">
                            Unlike generic OCR or naive converters that dump arbitrary text frames, DocLift reconstructs the logical semantic tree of your document.
                        </p>
                    </div>

                    <div className="shrink-0 inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-50 border border-emerald-200/80 text-emerald-800 text-xs sm:text-sm font-semibold self-start sm:self-center">
                        <CheckCircle className="size-4 text-emerald-600 shrink-0" />
                        <span>99.8% Word Style Parity</span>
                    </div>
                </div>

                {/* Bottom Section Cards */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                    {preservationData.map((item) => (
                        <PreservationCard
                            key={item.id}
                            item={item}
                        />
                    ))}
                </div>
            </section>
        </div>
    );
}
