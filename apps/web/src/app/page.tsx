"use client";
import Header from "../components/layout/Header";
import Footer from "../components/layout/Footer";
import { Button } from "@/components/ui/button";
import { toast } from "sonner";



export default function Home() {
  return (
    <div className="min-h-screen flex flex-col">
      <Header />
      <main className="flex-1">
        <div className="flex flex-col items-center justify-center h-full mt-10">
          <Button onClick={() => {
            toast.success("This is a success message!");
          }}>Show Success Toast</Button>
          </div>
        
        
      </main>

      <Footer />
    </div>
  );
}
