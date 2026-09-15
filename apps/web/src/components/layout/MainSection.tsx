"use client";

import { useRef, useState } from "react";
import DropZone, { DropZoneHandle } from "@/components/upload/DropZone";
import { FileText, ArrowLeftRight, Trash } from "lucide-react";
import Button from "@/components/ui/Button";

function formatFileSize(bytes: number): string {
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function MainSection() {
    const [uploadedFile, setUploadedFile] = useState<File | null>(null);
    const dropZoneRef = useRef<DropZoneHandle>(null);

    const handleFileDrop = (file: File) => {
        setUploadedFile(file);
    };

    const handleRemoveFile = () => {
        setUploadedFile(null);
        dropZoneRef.current?.clearFile();
    };

    return (
        <div className="flex-1 space-y-4 p-4">
            <div className="bg-white w-full mx-auto max-w-3xl flex flex-col space-y-4 p-4 rounded-xl">
                <div className="mt-8 max-w-2xl text-center mx-auto space-y-4">
                    <h1 className="text-4xl font-bold">
                        TRANSFORM YOUR PDF INTO EDITABLE WORD DOCUMENT
                    </h1>
                    <p>
                        Convert digital PDFs into editable Word documents while preserving text, structure, formatting, images, tables, and reading order.
                    </p>
                </div>
                <DropZone ref={dropZoneRef} onDrop={handleFileDrop}>
                    <div>
                        <div className="p-4 bg-blue-200 rounded-2xl">
                            <FileText className="size-15 text-blue-600"/>
                        </div>
                        <div className="relative w-fit left-1/2 top-1/2 -translate-y-1/2 translate-x-6 bg-blue-600 rounded-full p-2">
                            <ArrowLeftRight className="size-4 text-white"/>
                        </div>
                    </div>
                    <h1 className="flex justify-center text-2xl">Drag & drop your PDF here</h1>
                    <p className="flex justify-center">Drop files here</p>
                </DropZone>
                <div className="w-full flex justify-around p-2 items-center bg-blue-100 rounded-xl max-w-4xl mx-auto">
                    <div> <p>Supported format: <span className="font-semibold">PDF only (.pdf)</span></p></div>
                    <div className="rounded-full bg-black p-1 w-fit h-fit"></div>
                    <div><p>Limit: <span className="font-semibold">Up to 50 MB</span></p></div>
                    <div className="rounded-full bg-black p-1 w-fit h-fit"></div>
                    <div><p>Source: <span className="font-semibold">Digital text layer</span></p></div>
                </div>

                {uploadedFile && (
                    <div className="w-full flex flex-row items-center space-x-4 p-4 border border-gray-200 rounded-xl">
                        <FileText className="size-15 text-blue-600 shrink-0"/>
                        <div className="flex-1 flex flex-col space-y-2 min-w-0">
                            <p className="text-xl font-semibold truncate">{uploadedFile.name}</p>
                            <div className="text-sm bg-green-100 rounded-full w-fit px-2 py-1 space-x-2 text-green-500 flex items-center">
                                <span>Text layer verified</span>
                                <div className="rounded-full bg-green-500 p-0.5 w-fit h-fit"></div>
                                <span>Ready to reconstruct</span>
                            </div>
                            <div className="flex items-center space-x-2 text-sm text-gray-500">
                                <p>{formatFileSize(uploadedFile.size)}</p>
                            </div>
                        </div>
                        <Button variant="danger" onClick={handleRemoveFile}>
                            <Trash/>
                            Remove File
                        </Button>
                    </div>
                )}

                <Button disabled={!uploadedFile}>
                    <p className="text-xl">Convert to Word (.docx)</p>
                </Button>
            </div>
        </div>
    );
}