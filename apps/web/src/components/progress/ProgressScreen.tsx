import { useEffect, useState } from "react";
import ProgressCard from "./ProgressCard";
import HeaderBar from "./HeaderBar";
import { useConversion } from "@/context/ConversionContext";

const ProgressScreen = () => {
  const { file, session, setActiveView } = useConversion();

  const [timeElapsed, setTimeElapsed] = useState(0);
  const [timeRemaining, setTimeRemaining] = useState(0);

  useEffect(() => {
    if (session?.status === "done") {
      const timer = setTimeout(() => {
        setActiveView("result");
      }, 2000);
      return () => clearTimeout(timer);
    }
  }, [session?.status, setActiveView]);

  const isFailed = session?.status === "failed";

  return (
    <div className="w-full flex-1 px-4 py-2">
      {!isFailed && (
        <HeaderBar
          file={file}
          timeElapsed={timeElapsed}
          timeRemaining={timeRemaining}
        />
      )}
      <ProgressCard />
    </div>
  );
};

export default ProgressScreen;