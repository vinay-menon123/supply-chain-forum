/**
 * Runs `tick` on an interval, but PAUSES while the browser tab is hidden
 * (Page Visibility API) and fires once immediately when the tab is shown again
 * so the UI catches up instantly on refocus.
 *
 * Why this matters: every open tab that polls keeps hitting the API even when
 * nobody is looking at it. A backgrounded tab left open all day was our biggest
 * source of idle load — pausing on `document.hidden` removes it entirely, which
 * is what lets us scale to many concurrent users (and later run multiple
 * replicas) without the message/unread pollers dominating traffic.
 *
 * Note: this does NOT fire `tick` on start — callers already do an initial
 * fetch of their own, so we avoid a duplicate request on mount. Returns a
 * cleanup function; call it in the effect's teardown.
 */
export function startVisibilityInterval(tick: () => void, intervalMs: number): () => void {
  let timer: ReturnType<typeof setInterval> | undefined;

  const startTimer = () => {
    if (timer === undefined && !document.hidden) {
      timer = setInterval(tick, intervalMs);
    }
  };
  const stopTimer = () => {
    if (timer !== undefined) {
      clearInterval(timer);
      timer = undefined;
    }
  };
  const onVisibility = () => {
    if (document.hidden) {
      stopTimer();
    } else {
      tick(); // catch up immediately on refocus
      startTimer();
    }
  };

  startTimer();
  document.addEventListener("visibilitychange", onVisibility);

  return () => {
    document.removeEventListener("visibilitychange", onVisibility);
    stopTimer();
  };
}
