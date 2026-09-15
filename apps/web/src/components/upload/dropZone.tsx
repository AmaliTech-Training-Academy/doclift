"use client";

import { ReactNode, useState, useRef } from "react";
import { Input } from "@/components/ui/Input";
import { cn } from "@/lib/utils";

interface DropZoneProps {
    children?: ReactNode;
    onDrop?: (files: FileList) => void;
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

        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            onDrop?.(e.dataTransfer.files);
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
                "max-w-4xl p-8 m-4 mx-auto border-2 flex flex-col items-center justify-center bg-white border-dashed border-gray-300 hover:border-blue-500 cursor-pointer rounded-xl transition-all duration-200 ease-in-out select-none",
                isDragActive && "border-blue-500 bg-blue-50 scale-[1.01] shadow-xl"
            )}
        >
            <div className="pointer-events-none flex flex-col items-center justify-center w-full">
                {children}
            </div>
            <Input 
                ref={fileInputRef}
                type="file" 
                accept="application/pdf"
                multiple={true} 
                onChange={(e) => {
                    if (e.target.files) {
                        onDrop?.(e.target.files);
                    }
                }}
                onClick={(e) => e.stopPropagation()}
                className="w-fit mt-4 border-blue-400 bg-blue-100 text-blue-600 hover:bg-blue-200 hover:shadow-md cursor-pointer transition-all"
            />
        </div>
    );
}