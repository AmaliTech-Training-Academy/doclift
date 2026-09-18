import React, { useState } from "react";
import ProgressCard from "./ProgressCard";
import HeaderBar from "./HeaderBar";
import { useConversion } from "@/context/ConversionContext";


const ProgressScreen = () => {
  const { file } = useConversion();

  const [timeElapsed, setTimeElapsed] = useState(0);
  const [timeRemaining, setTimeRemaining] = useState(0);

  return (
    <div className="w-full flex-1 px-4 py-2">
      <HeaderBar
        file={file}
        timeElapsed={timeElapsed}
        timeRemaining={timeRemaining}
      />
      <ProgressCard />
    </div>
  );
};

export default ProgressScreen;