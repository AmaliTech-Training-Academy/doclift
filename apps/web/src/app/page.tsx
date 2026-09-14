import type { Metadata } from "next";
import DropZone from "@/components/upload/dropZone";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { FileText, ArrowLeftRight } from "lucide-react";

export const metadata: Metadata = {
  title: "DocLift - Convert",
};

export default function Home() {
  return (
    <div className="min-h-dvh bg-blue-50 flex flex-col">
      <main className="flex-1">
        <DropZone>
          <div className="">
            <div className="p-4 bg-blue-100 rounded-2xl">
              <FileText className="size-15 text-blue-600" />
            </div>
            <div className="relative w-fit left-1/2 top-1/2 -translate-y-1/2 translate-x-6 bg-blue-600 rounded-full p-2">
              <ArrowLeftRight className="size-4 text-white" />
            </div>
          </div>
          <h1 className="flex justify-center text-2xl">
            Drag & drop your PDF here
          </h1>
          <p className="flex justify-center">Drop files here</p>
        </DropZone>
        <Card>
          <CardHeader>
            <CardTitle>Convert PDF to Word Online</CardTitle>
            <CardDescription>
              Convert PDF files to Word documents
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p>
              {" "}
              Convert your PDF files to Word documents with ease. Simply upload
              your PDF and get a Word document in seconds.
            </p>
          </CardContent>
          <CardFooter>
            <p>page content</p>
          </CardFooter>
        </Card>
        {/* page content */}
      </main>
    </div>
  );
}
