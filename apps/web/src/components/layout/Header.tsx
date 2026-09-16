"use client";

import { Plus } from "lucide-react";
import Button from "@/components/ui/Button";
import Image from "next/image";
import Logo from "@/app/assets/logo.png";
import { useConversion } from "@/context/ConversionContext";

export default function Header() {
    const { reset } = useConversion();

    return (
        <header className="w-full py-3 border-b border-gray-200 bg-white">
            <div className="max-w-7xl mx-auto px-4">
                <div className="flex items-center justify-between">
                    <div className="flex flex-col">
                        <Image priority src={Logo} alt="Logo" height={60}/>
                    </div>
                    <div className="flex items-center space-x-4">
                        <Button variant="secondary" onClick={reset}>
                            <Plus/>
                            New Conversion
                        </Button>
                    </div>
                </div>
            </div>
        </header>
    );
}