import Header from "./components/layout/Header";
import Footer from "./components/layout/Footer";

export default function Home() {
    return (
        <div className="min-h-screen flex flex-col">
            <Header />
            <main className="flex-1">
                {/* Your page content */}
            </main>

            <Footer />
        </div>
    );
}
