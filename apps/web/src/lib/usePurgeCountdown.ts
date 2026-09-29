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
    if (!completedAt) return;
    const tick = () => setNow(Date.now());
    // Refresh right away so a stale `now` doesn't linger for a full second.
    const timeout = setTimeout(tick, 0);
    const id = setInterval(tick, 1000);
    return () => {
      clearTimeout(timeout);
      clearInterval(id);
    };
  }, [completedAt]);

  if (completedAt === undefined) return null;
 
  const elapsed = Math.max(0, Math.floor((now - completedAt) / 1000));
  return Math.max(0, ttlSeconds - elapsed);
}
