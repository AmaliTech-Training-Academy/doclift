import { useEffect, useState } from "react";

/** How long the server keeps conversion artifacts before purging them. */
export const PURGE_TTL_SECONDS = 60 * 60;

/**
 * Seconds left until the conversion cache is purged, ticking every second.
 * Returns null when there is no completion time to count down from.
 */
export function usePurgeCountdown(
  completedAt?: number,
  ttlSeconds: number = PURGE_TTL_SECONDS,
): number | null {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (completedAt === undefined) return;
    setNow(Date.now());
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [completedAt]);

  if (completedAt === undefined) return null;
  const elapsed = Math.floor((now - completedAt) / 1000);
  return Math.max(0, ttlSeconds - elapsed);
}
