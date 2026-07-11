import { useEffect, useRef } from "react";

/**
 * Subtle drifting connected-node canvas behind the app. Tuned to stay cheap on
 * phones: the node count scales with screen width, the loop pauses when the tab
 * is hidden, and it renders nothing at all when the user prefers reduced motion.
 */
export default function BackgroundNetwork() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    // Respect the OS "reduce motion" setting — skip the animation entirely.
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) {
      return;
    }

    // Performance optimization: skip execution entirely on mobile devices
    if (window.innerWidth < 768) {
      return;
    }

    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let animationFrameId = 0;
    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    type Particle = { x: number; y: number; vx: number; vy: number; radius: number; alpha: number; pulseSpeed: number };
    let particles: Particle[] = [];

    // Scale node count to the viewport so phones do far less work than desktops
    // (~8 nodes on a 375px phone, capped at 40 on wide screens). The connection
    // pass is O(n²), so keeping n small on mobile is what keeps this smooth.
    const buildParticles = () => {
      const count = Math.max(6, Math.min(40, Math.round(width / 46)));
      particles = Array.from({ length: count }, () => ({
        x: Math.random() * width,
        y: Math.random() * height,
        vx: (Math.random() - 0.5) * 0.22,
        vy: (Math.random() - 0.5) * 0.22,
        radius: Math.random() * 1.5 + 0.8,
        alpha: Math.random() * 0.4 + 0.25,
        pulseSpeed: Math.random() * 0.015 + 0.003,
      }));
    };

    const handleResize = () => {
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
      buildParticles();
    };

    buildParticles();
    window.addEventListener("resize", handleResize);

    const draw = () => {
      ctx.clearRect(0, 0, width, height);

      particles.forEach((p) => {
        p.x += p.vx;
        p.y += p.vy;
        if (p.x < 0 || p.x > width) p.vx *= -1;
        if (p.y < 0 || p.y > height) p.vy *= -1;
        p.alpha += p.pulseSpeed;
        if (p.alpha > 0.8 || p.alpha < 0.2) p.pulseSpeed *= -1;
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(94, 106, 210, ${p.alpha})`;
        ctx.fill();
      });

      for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
          const p1 = particles[i];
          const p2 = particles[j];
          const dx = p1.x - p2.x;
          const dy = p1.y - p2.y;
          const dist = Math.sqrt(dx * dx + dy * dy);
          if (dist < 130) {
            ctx.beginPath();
            ctx.moveTo(p1.x, p1.y);
            ctx.lineTo(p2.x, p2.y);
            ctx.strokeStyle = `rgba(94, 106, 210, ${(1 - dist / 130) * 0.15})`;
            ctx.lineWidth = 0.7;
            ctx.stroke();
          }
        }
      }

      animationFrameId = requestAnimationFrame(draw);
    };

    // Pause the loop while the tab is backgrounded to save battery/CPU.
    const start = () => {
      if (!animationFrameId) draw();
    };
    const stop = () => {
      if (animationFrameId) {
        cancelAnimationFrame(animationFrameId);
        animationFrameId = 0;
      }
    };
    const handleVisibility = () => (document.hidden ? stop() : start());
    document.addEventListener("visibilitychange", handleVisibility);

    start();

    return () => {
      window.removeEventListener("resize", handleResize);
      document.removeEventListener("visibilitychange", handleVisibility);
      stop();
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="fixed inset-0 -z-10 h-full w-full pointer-events-none opacity-[0.35]"
    />
  );
}
