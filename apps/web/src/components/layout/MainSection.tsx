"use client";

import DropZone from "@/components/upload/DropZone";
import {FileText, ArrowLeftRight} from "lucide-react"
import Button from "@/components/ui/Button";

export default function MainSection() {
    return (
        <div className="flex-1 space-y-4 p-4">
            <div className="bg-white w-full mx-auto max-w-3xl flex flex-col space-y-4 p-4 rounded-lg">
                <div className="mt-8 max-w-2xl text-center mx-auto space-y-4">
                    <p className="text-4xl font-bold">
                        TRANSFORM YOUR PDF INTO EDITABLE WORD DOCUMENT
                    </p>
                    <p>
                        Convert digital PDFs into editable Word documents while preserving text, structure, formatting, images, tables, and reading order.
                    </p>
                </div>
                <DropZone>
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

                <div>
                    {/* Todo: Uploaded file details */}
                </div>

                <Button>
                    <p className="text-xl">Convert to Word (.docx)</p>
                </Button>
            </div>

            <div>

            </div>
        </div>
    );
}