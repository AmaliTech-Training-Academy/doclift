import { Plus } from "lucide-react";
import Button from "@/app/components/ui/Button";
import Image from "next/image";
import Logo from "@/app/assets/logo.png"

export default function Header() {
    return (
        <header className="sticky top-0 w-full py-3 border-b border-gray-200 bg-white z-50">
            <div className="max-w-7xl mx-auto px-4">
                <div className="flex items-center justify-between">
                    <div className="flex flex-col">
                        <Image src={Logo} alt="Logo" height={60}/>
                        <p className="text-lg font-semibold text-gray-900">Convert PDF to Word</p>
                    </div>
                    <div className="flex items-center space-x-4">
                        <div className="flex items-center space-x-2 rounded-full px-3 py-1 bg-blue-100">
                            <span className="text-lg text-black">Desktop Client v1.0</span>
                        </div>
                        <Button variant="ghost">
                            Documentation
                        </Button>
                        <Button variant="secondary">
                            <Plus/>
                            New Conversion
                        </Button>
                    </div>
                </div>
            </div>
        </header>
    );
}