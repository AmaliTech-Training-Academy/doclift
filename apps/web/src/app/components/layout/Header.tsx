

export default function Header() {
    return (
        <header className="w-full py-3 border-b border-gray-200 bg-white">
            <div className="max-w-7xl mx-auto px-4">
                <div className="flex items-center justify-between">
                    <div className="flex flex-col space-y-1">
                        <h1 className="text-2xl font-bold text-gray-900">DocLift</h1>
                        <h1 className="text-lg font-bold text-gray-900">DocLift</h1>
                    </div>
                    <div className="flex items-center space-x-4">
                        <div className="flex items-center space-x-2 rounded-full px-3 py-1 bg-blue-100">
                            <span className="text-lg text-black">Desktop Client v1.0</span>
                        </div>
                        <span className="text-lg text-gray-900">Documentaion</span>
                        <span className="text-lg text-gray-900">+ New conversion</span>
                    </div>
                </div>
            </div>
        </header>
    );
}