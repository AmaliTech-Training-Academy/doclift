"use client";

import { useRef } from "react";
import DropZone, { DropZoneHandle } from "@/components/upload/DropZone";
import { FileText, ArrowLeftRight, Trash, CheckCircle } from "lucide-react";
import { Button } from "@/components/ui/Button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/Card";
import ProgressCard from "../progress/ProgressCard";
import HeaderBar from "../progress/HeaderBar";
import Button from "@/components/ui/Button";
import { Card, CardContent, CardDescription, CardHeader } from "@/components/ui/Card";
import { useConversion } from "@/context/ConversionContext";

function formatFileSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function MainSection() {
  // const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  // const dropZoneRef = useRef<DropZoneHandle>(null);

  // const handleFileDrop = (file: File) => {
  //     setUploadedFile(file);
  // };

  // const handleRemoveFile = () => {
  //     setUploadedFile(null);
  //     dropZoneRef.current?.clearFile();
  // };

  return (
    <>
      <HeaderBar />
      <ProgressCard />
    </>
    // <div className="flex-1 space-y-4 p-4">
    //     <div className="bg-white w-full mx-auto max-w-3xl flex flex-col space-y-4 p-4 mb-10 rounded-xl">
    //         <div className="mt-8 max-w-2xl text-center mx-auto space-y-4">
    //             <h1 className="text-4xl font-bold">
    //                 TRANSFORM YOUR PDF INTO EDITABLE WORD DOCUMENT
    //             </h1>
    //             <p>
    //                 Convert digital PDFs into editable Word documents while preserving text, structure, formatting, images, tables, and reading order.
    //             </p>
    //         </div>
    //         <DropZone ref={dropZoneRef} onDrop={handleFileDrop}>
    //             <div>
    //                 <div className="p-4 bg-blue-200 rounded-2xl">
    //                     <FileText className="size-15 text-blue-600"/>
    //                 </div>
    //                 <div className="relative w-fit left-1/2 top-1/2 -translate-y-1/2 translate-x-6 bg-blue-600 rounded-full p-2">
    //                     <ArrowLeftRight className="size-4 text-white"/>
    //                 </div>
    //             </div>
    //             <h1 className="flex justify-center text-2xl">Drag & drop your PDF here</h1>
    //             <p className="flex justify-center">Drop files here</p>
    //         </DropZone>
    //         <div className="w-full flex justify-around p-2 items-center bg-blue-100 rounded-xl max-w-4xl mx-auto">
    //             <div> <p>Supported format: <span className="font-semibold">PDF only (.pdf)</span></p></div>
    //             <div className="rounded-full bg-black p-1 w-fit h-fit"></div>
    //             <div><p>Limit: <span className="font-semibold">Up to 50 MB</span></p></div>
    //             <div className="rounded-full bg-black p-1 w-fit h-fit"></div>
    //             <div><p>Source: <span className="font-semibold">Digital text layer</span></p></div>
    //         </div>

    //         {uploadedFile && (
    //             <div className="w-full flex flex-row items-center space-x-4 p-4 border border-gray-200 rounded-xl">
    //                 <FileText className="size-15 text-blue-600 shrink-0"/>
    //                 <div className="flex-1 flex flex-col space-y-2 min-w-0">
    //                     <p className="text-xl font-semibold truncate">{uploadedFile.name}</p>
    //                     <div className="text-sm bg-blue-100 rounded-lg w-fit px-2 py-1 space-x-2 text-blue-600">
    //                         <span>PDF selected</span>
    //                     </div>
    //                     <div className="flex items-center space-x-2 text-sm text-gray-500">
    //                         <p>{formatFileSize(uploadedFile.size)}</p>
    //                     </div>
    //                 </div>
    //                 <Button variant="destructive" onClick={handleRemoveFile}>
    //                     <Trash/>
    //                     Remove File
    //                 </Button>
    //             </div>
    //         )}

    //         <Button disabled={!uploadedFile}>
    //             <p className="text-xl">Convert to Word (.docx)</p>
    //         </Button>

    //     </div>
    //     {/* Bottom Section*/}
    //         <div className="w-full flex flex-col space-y-4 px-8 py-4 bg-blue-100">
    //             <div className="flex flex-row justify-between">
    //                 <div className="flex flex-col">
    //                     <h1 className="text-2xl font-bold">What DocLift Preserves</h1>
    //                     <p className="text-sm">Unlike generic OCR or naive converters that dump arbitrary text frames, DocLift reconstructs the logical semantic tree of your document.</p>

    //                 </div>
    //                 <div className="inline-flex items-center space-x-2">
    //                     <CheckCircle className="size-4 text-green-500"/>
    //                     <p className="text-sm">99.8% Word Style Parity</p>
    //                 </div>
    //             </div>
    //             <div className="grid grid-cols-3 gap-4">
    //                  <Card className="mx-auto w-full">
    //                     <CardHeader>
    //                         <CardDescription className="rounded-xl bg-blue-100 p-2 w-fit h-fit flex">
    //                             <FileText className="size-10 text-blue-600"/>
    //                         </CardDescription>
    //                     </CardHeader>
    //                     <CardContent>
    //                         <p className="text-xl font-semibold mb-2">Reading Order</p>
    //                         <p className="mb-2">Rebuilds multi-column & asymmetric column flows naturally.</p>

    //                         <div className="bg-blue-100 flex flex-col rounded-lg p-4">
    //                             <div className="flex flex-row justify-between mb-2">
    //                                 <p className="text-sm">Column Flow</p>
    //                                 <p className="text-sm">Contiguous Flow</p>
    //                             </div>
    //                             <div className="flex flex-row">
    //                                 <p className="text-sm">Rebuilds multi-column & asymmetric column flows naturally.</p>
    //                             </div>
    //                         </div>
    //                     </CardContent>
    //                 </Card>
    //             </div>
    //         </div>
    // </div>
  );
}
