"use client";

import { ReactNode, useState, useRef } from "react";
import { Input } from "@/components/ui/Input";
import { cn } from "@/lib/utils";
import { toast } from "sonner"

interface DropZoneProps {
    children?: ReactNode;
    onDrop?: (files: File) => void;
    onDragOver?: (e: React.DragEvent<HTMLDivElement>) => void;
    onDragEnter?: (e: React.DragEvent<HTMLDivElement>) => void;
    onDragLeave?: (e: React.DragEvent<HTMLDivElement>) => void;
    isDragActive?: boolean;
}

export default function DropZone ({children, onDrop, onDragOver, onDragEnter, onDragLeave, isDragActive: externalIsDragActive}: DropZoneProps) {
    const [internalIsDragActive, setInternalIsDragActive] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const dragCounter = useRef(0);

    const isDragActive = externalIsDragActive ?? internalIsDragActive;

    const handleFile = (file: File) => {
        if (
            file.type !== "application/pdf" &&
            !file.name.toLowerCase().endsWith(".pdf")
        ) {
            toast.error("Invalid file", {
                description: "Only PDF files are allowed.",
            });

            return;
        }

        toast.success("File selected", {
            description: `${file.name} is ready for conversion.`,
        });

        onDrop?.(file);
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

    const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();
        dragCounter.current = 0;
        setInternalIsDragActive(false);

        const file = e.dataTransfer.files[0];
        if (!file) return;

        handleFile(file);

        const dataTransfer = new DataTransfer();
        dataTransfer.items.add(file);

        if (fileInputRef.current) {
            fileInputRef.current.files = dataTransfer.files;
        }
    };

    const handleClick = () => {
        fileInputRef.current?.click();
    };

    return (
        <div 
            onClick={handleClick}
            onDragEnter={handleDragEnter}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            className={cn(
                "group max-w-4xl w-full p-8 m-4 mx-auto border-2 flex flex-col items-center justify-center bg-blue-50 hover:bg-blue-100 border-dashed border-gray-300 hover:border-blue-500 cursor-pointer rounded-xl transition-all active:border-solid active:scale-[1.01] duration-200 ease-in-out select-none",
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
                onChange={(e) => {
                    if (e.target.files && e.target.files.length > 0) {
                        handleFile(e.target.files[0]);
                    }
                }}
                onClick={(e) => e.stopPropagation()}
                className="w-fit mt-4 border-blue-400 bg-blue-100 text-blue-600 hover:bg-blue-200 hover:shadow-md cursor-pointer transition-all"
            />
        </div>
    );
}