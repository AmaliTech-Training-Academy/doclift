"use client";

import { ReactNode, useState, useRef, forwardRef, useImperativeHandle } from "react";
import { Input } from "@/components/ui/Input";
import { cn } from "@/lib/utils";
import { toast } from "sonner";

interface DropZoneProps {
    children?: ReactNode;
    onDrop?: (file: File) => void;
    onDragOver?: (e: React.DragEvent<HTMLDivElement>) => void;
    onDragEnter?: (e: React.DragEvent<HTMLDivElement>) => void;
    onDragLeave?: (e: React.DragEvent<HTMLDivElement>) => void;
    isDragActive?: boolean;
}

export interface DropZoneHandle {
    clearFile: () => void;
}

// validating if the file is a pdf (checks for the %PDF- header)
async function validatePdfHeader(file: File): Promise<boolean> {
    try {
        const slice = file.slice(0, 5);
        const buffer = await slice.arrayBuffer();
        const header = new Uint8Array(buffer);
        // %PDF- header magic bytes: 0x25 ('%'), 0x50 ('P'), 0x44 ('D'), 0x46 ('F'), 0x2D ('-')
        return (
            header.length >= 5 &&
            header[0] === 0x25 &&
            header[1] === 0x50 &&
            header[2] === 0x44 &&
            header[3] === 0x46 &&
            header[4] === 0x2D
        );
    } catch {
        return false;
    }
}

// checking if the file is encrypted (scans head and tail of file for /Encrypt keyword)
async function checkPdfEncrypted(file: File): Promise<boolean> {
    try {
        const size = file.size;
        const chunkSize = 1024 * 1024; // 1 MB

        // For small files (<= 2MB), scan the entire file text
        if (size <= 2 * chunkSize) {
            const text = await file.text();
            return /\/Encrypt\b/.test(text);
        }

        // For larger files, scan both head (first 1MB) and tail (last 1MB where trailer sits)
        const headText = await file.slice(0, chunkSize).text();
        const tailText = await file.slice(size - chunkSize, size).text();

        return /\/Encrypt\b/.test(headText) || /\/Encrypt\b/.test(tailText);
    } catch {
        return false;
    }
}

const DropZone = forwardRef<DropZoneHandle, DropZoneProps>(function DropZone(
    {children, onDrop, onDragOver, onDragEnter, onDragLeave, isDragActive: externalIsDragActive}: DropZoneProps,
    ref
) {
    const [internalIsDragActive, setInternalIsDragActive] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const dragCounter = useRef(0);
    const validationIdRef = useRef(0);

    const isDragActive = externalIsDragActive ?? internalIsDragActive;

    useImperativeHandle(ref, () => ({
        clearFile() {
            validationIdRef.current += 1;
            if (fileInputRef.current) {
                fileInputRef.current.value = "";
            }
        },
    }));

    const MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB

    const handleFile = async (file: File): Promise<boolean> => {
        const currentValidationId = ++validationIdRef.current;

        const hasPdfExtension = file.name.toLowerCase().endsWith(".pdf");
        const hasPdfMime = file.type === "application/pdf" || file.type === "";

        if (!hasPdfExtension || !hasPdfMime) {
            if (currentValidationId === validationIdRef.current) {
                toast.error("Invalid file", {
                    description: "Only PDF files are allowed.",
                });
            }
            return false;
        }

        if (file.size === 0) {
            if (currentValidationId === validationIdRef.current) {
                toast.error("Empty file", {
                    description: "The selected PDF file is empty.",
                });
            }
            return false;
        }

        if (file.size === 0) {
            toast.error("Empty file", {
                description: "The selected PDF file is empty.",
            });
            return false;
        }

        if (file.size > MAX_FILE_SIZE_BYTES) {
            if (currentValidationId === validationIdRef.current) {
                toast.error("File too large", {
                    description: "File size exceeds the 50 MB limit.",
                });
            }
            return false;
        }

        const isHeaderValid = await validatePdfHeader(file);
        if (currentValidationId !== validationIdRef.current) return false;

        if (!isHeaderValid) {
            toast.error("Invalid PDF file", {
                description: "File content is not a valid PDF document.",
            });
            return false;
        }

        const isEncrypted = await checkPdfEncrypted(file);
        if (currentValidationId !== validationIdRef.current) return false;

        if (isEncrypted) {
            toast.error("Encrypted PDF", {
                description: "Password-protected or encrypted PDF files are not supported.",
            });
            return false;
        }

        toast.success("File selected", {
            description: `${file.name} is ready for conversion.`,
        });

        onDrop?.(file);
        return true;
    };

    const handleDragEnter = (e: React.DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();
        dragCounter.current += 1;
        if (dragCounter.current === 1) {
            setInternalIsDragActive(true);
        }
        onDragEnter?.(e);
    };

    const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();
        onDragOver?.(e);
    };

    const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();
        dragCounter.current -= 1;
        if (dragCounter.current <= 0) {
            dragCounter.current = 0;
            setInternalIsDragActive(false);
        }
        onDragLeave?.(e);
    };

    const handleDrop = async (e: React.DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();
        dragCounter.current = 0;
        setInternalIsDragActive(false);

        const file = e.dataTransfer.files[0];
        if (!file) return;

        const isValid = await handleFile(file);
        if (!isValid) return;

        const dataTransfer = new DataTransfer();
        dataTransfer.items.add(file);

        if (fileInputRef.current) {
            fileInputRef.current.files = dataTransfer.files;
        }
    };

    const handleClick = () => {
        fileInputRef.current?.click();
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
        if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            handleClick();
        }
    };

    return (
        <div 
            role="button"
            tabIndex={0}
            aria-label="Upload PDF file"
            onClick={handleClick}
            onKeyDown={handleKeyDown}
            onDragEnter={handleDragEnter}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            className={cn(
                "group max-w-4xl w-full p-8 m-4 mx-auto border-2 flex flex-col items-center justify-center bg-blue-50 hover:bg-blue-100 border-dashed border-gray-300 hover:border-blue-500 cursor-pointer rounded-xl transition-all active:border-solid active:scale-[1.01] duration-200 ease-in-out select-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2",
                isDragActive && "border-blue-500 bg-blue-100 scale-[1.01] shadow-lg"
            )}
        >
            <div className="pointer-events-none flex flex-col items-center justify-center w-full">
                {children}
            </div>
            <Input 
                ref={fileInputRef}
                type="file" 
                accept="application/pdf"
                onChange={async (e) => {
                    if (e.target.files && e.target.files.length > 0) {
                        const isValid = await handleFile(e.target.files[0]);
                        if (!isValid && fileInputRef.current) {
                            fileInputRef.current.value = "";
                        }
                    }
                }}
                onClick={(e) => e.stopPropagation()}
                className="w-fit mt-4 border-blue-400 bg-blue-100 text-blue-600 hover:bg-blue-200 hover:shadow-md cursor-pointer transition-all"
            />
        </div>
    );
});

export default DropZone;