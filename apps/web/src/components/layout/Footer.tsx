import Button from "../ui/Button";

export default function Footer() {
    return (
        <footer className="w-full py-3 border-t border-gray-200 bg-white">
            <div className="max-w-[90%] mx-auto px-4">
                <div className="flex items-center justify-between space-x-4">
                    <span className="text-lg text-gray-900">Client-side encrypted transport • Files deleted automatically after 60 minutes • Output strictly Microsoft Word (.docx)</span>
                    <div className="flex items-center space-x-4">
                        <Button variant="ghost">
                            Format Support
                        </Button>
                        <Button variant="ghost">
                            Documentaion
                        </Button>
                        <Button variant="ghost">
                            Integrity & Privacy
                        </Button>
                    </div>
                </div>
            </div>
        </footer>
    );
}