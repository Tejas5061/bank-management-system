import { useEffect, useState, useSyncExternalStore } from 'react';

/**
 * The public demo's API runs on a free host that sleeps after 15 idle minutes and takes a minute
 * or two to wake. In demo builds we ping it on load, and if it hasn't answered within a few
 * seconds the UI says why the page is waiting instead of showing a spinner with no explanation.
 */
const DEMO_BUILD = import.meta.env.VITE_DEMO_MODE === 'true';
const PING_URL = `${import.meta.env.VITE_API_URL ?? '/api/v1'}/public/branches`;
const SLOW_AFTER_MS = 3_000;
const STUCK_AFTER_MS = 180_000;

let awake = false;
let started = false;
const listeners = new Set<() => void>();

function startPinging() {
  if (started) return;
  started = true;
  const startedAt = Date.now();
  const ping = async () => {
    try {
      const response = await fetch(PING_URL, { cache: 'no-store' });
      if (response.ok) {
        awake = true;
        listeners.forEach((notify) => notify());
        return;
      }
    } catch {
      // Network error or proxy timeout while the host boots: try again below.
    }
    // Past the "stuck" point the notice asks the visitor to refresh; stop polling a dead server.
    if (Date.now() - startedAt < STUCK_AFTER_MS) window.setTimeout(() => void ping(), 4_000);
  };
  void ping();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export type WakeState = 'awake' | 'waking' | 'stuck';

export function useServerWake(): WakeState {
  const isAwake = useSyncExternalStore(subscribe, () => awake);
  const [elapsed, setElapsed] = useState<'fresh' | 'slow' | 'stuck'>('fresh');

  useEffect(() => {
    if (!DEMO_BUILD) return undefined;
    startPinging();
    const slow = window.setTimeout(() => setElapsed('slow'), SLOW_AFTER_MS);
    const stuck = window.setTimeout(() => setElapsed('stuck'), STUCK_AFTER_MS);
    return () => {
      window.clearTimeout(slow);
      window.clearTimeout(stuck);
    };
  }, []);

  if (!DEMO_BUILD || isAwake || elapsed === 'fresh') return 'awake';
  return elapsed === 'stuck' ? 'stuck' : 'waking';
}
