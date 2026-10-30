// The courier's world, from the driver's seat at night, in black and white: rain on the glass,
// streetlights passing overhead, soft city lights, the road rushing in, the dash at the bottom.
// The only colour is the verdict's, spilling onto the glass and the dash while a card is up.
import React, { useMemo } from "react";
import { interpolate } from "remotion";
import { VerdictColor } from "./tokens";
import { impactAt } from "./fx";

const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;

/** A seeded random for stable layouts. */
const seeded = (s: number) => () => ((s = (s * 16807) % 2147483647) / 2147483647);

export const Drive: React.FC<{ t: number; W: number; H: number; zoom?: number }> = ({ t, W, H, zoom = 1 }) => {
  const wide = W > H;
  const hz = H * (wide ? 0.44 : 0.36); // horizon
  const dashY = H * (wide ? 0.84 : 0.8); // top of the dash at the centre
  const speed = 0.55; // road units per second

  const { lights, drops, streaks } = useMemo(() => {
    const r = seeded(11);
    return {
      // Far city lights near the horizon (bokeh).
      lights: Array.from({ length: 60 }, () => ({ x: r(), y: r(), s: 0.5 + r() * 1.8, tw: r() * 6, b: 0.45 + r() * 0.55, drift: (r() - 0.5) * 0.02 })),
      // Raindrops sitting on the glass.
      drops: Array.from({ length: 70 }, () => ({ x: r(), y: r() * 0.8, s: 3 + r() * 9 })),
      // Drops running down the glass.
      streaks: Array.from({ length: 14 }, () => ({ x: r(), y0: r(), v: 0.04 + r() * 0.08, len: 40 + r() * 90 })),
    };
  }, []);

  // Streetlights pass overhead every 1.6 s, alternating sides.
  const LAMP_GAP = 1.6;
  const lamps = [0, 1, 2].map((k) => {
    const phase = ((t / LAMP_GAP + k / 3) % 1 + 1) % 1; // 0 far … 1 overhead
    const side = Math.floor(t / LAMP_GAP + k / 3) % 2 ? 1 : -1;
    const p = phase ** 2.4;
    return { p, x: W / 2 + side * (W * 0.08 + p * W * 0.9), y: hz - p * hz * 1.1, r: 6 + p * (wide ? 260 : 220), a: Math.min(1, phase * 2) * (1 - Math.max(0, phase - 0.9) * 10) };
  });
  // A light sweep inside the car as each lamp passes overhead.
  const sweep = Math.max(...[0, 1, 2].map((k) => {
    const phase = ((t / LAMP_GAP + k / 3) % 1 + 1) % 1;
    return interpolate(phase, [0.82, 0.92, 1], [0, 1, 0], clamp);
  }));

  // The verdict's colour spills onto the glass and the dash.
  const hit = impactAt(t);
  const spill = hit && hit.u >= 0 ? interpolate(t, [hit.scene[0] + 1.0, hit.scene[0] + 1.25, hit.scene[1] - 0.35, hit.scene[1]], [0, 1, 1, 0], clamp) : 0;
  const spillC = hit ? VerdictColor[hit.e.verdict] : "#fff";

  const roadW = W * 1.6;
  const dashes = Array.from({ length: 7 }, (_, i) => ((t * speed + i / 7) % 1));

  return (
    <div style={{ position: "absolute", inset: 0, overflow: "hidden", scale: String(1 + (zoom - 1) * 0.25) }}>
      {/* Night sky and glow of the city on the horizon */}
      <div style={{ position: "absolute", inset: 0, background: `linear-gradient(#000 0%, #0c0c0c ${(hz / H) * 60}%, #2e2e2e ${(hz / H) * 100}%, #0a0a0a ${(hz / H) * 100 + 2}%, #000 100%)` }} />
      {/* City bokeh */}
      {lights.map((l, i) => {
        const x = ((l.x + t * l.drift) % 1 + 1) % 1;
        const size = (wide ? 34 : 40) * l.s;
        const a = l.b * (0.7 + 0.3 * Math.sin(t * 2 + l.tw));
        return (
          <div
            key={i}
            style={{
              position: "absolute",
              left: x * W - size / 2,
              top: hz - size / 2 - l.y * H * 0.12,
              width: size,
              height: size,
              borderRadius: "50%",
              background: `radial-gradient(circle, rgba(255,255,255,${a}) 0%, rgba(255,255,255,${a * 0.35}) 45%, transparent 70%)`,
            }}
          />
        );
      })}
      {/* The road: a dark wedge with lane dashes rushing in */}
      <svg width={W} height={H} style={{ position: "absolute", inset: 0 }}>
        <defs>
          <linearGradient id="road" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#1c1c1c" />
            <stop offset="1" stopColor="#0a0a0a" />
          </linearGradient>
        </defs>
        <path d={`M${W / 2 - 6} ${hz} L${W / 2 + 6} ${hz} L${W / 2 + roadW / 2} ${H} L${W / 2 - roadW / 2} ${H} Z`} fill="url(#road)" />
        {dashes.map((d, i) => {
          const p0 = d ** 2.2, p1 = Math.min(1, d + 0.05) ** 2.2;
          const y0 = hz + (H - hz) * p0, y1 = hz + (H - hz) * p1;
          const w0 = 1 + p0 * 26, w1 = 1 + p1 * 26;
          return <path key={i} d={`M${W / 2 - w0 / 2} ${y0} L${W / 2 + w0 / 2} ${y0} L${W / 2 + w1 / 2} ${y1} L${W / 2 - w1 / 2} ${y1} Z`} fill={`rgba(255,255,255,${0.3 + p0 * 0.65})`} />;
        })}
        {[-1, 1].map((s) => (
          <path key={s} d={`M${W / 2 + s * 8} ${hz} L${W / 2 + s * roadW * 0.42} ${H}`} stroke="rgba(255,255,255,0.3)" strokeWidth={4} />
        ))}
      </svg>
      {/* Streetlights */}
      {lamps.map((l, i) => (
        <div
          key={i}
          style={{
            position: "absolute",
            left: l.x - l.r,
            top: l.y - l.r,
            width: l.r * 2,
            height: l.r * 2,
            borderRadius: "50%",
            opacity: Math.max(0, l.a),
            background: "radial-gradient(circle, rgba(255,255,255,0.95) 0%, rgba(255,255,255,0.35) 22%, rgba(255,255,255,0.08) 50%, transparent 70%)",
          }}
        />
      ))}
      {/* Rain on the glass */}
      {drops.map((d, i) => (
        <div
          key={i}
          style={{
            position: "absolute",
            left: d.x * W,
            top: d.y * H,
            width: d.s,
            height: d.s * 1.15,
            borderRadius: "50%",
            background: "radial-gradient(circle at 35% 30%, rgba(255,255,255,0.55), rgba(255,255,255,0.08) 45%, rgba(0,0,0,0.25) 70%)",
          }}
        />
      ))}
      {streaks.map((s, i) => {
        const y = ((s.y0 + t * s.v) % 1) * H * 0.85;
        return <div key={i} style={{ position: "absolute", left: s.x * W, top: y, width: 3, height: s.len, borderRadius: 2, background: "linear-gradient(transparent, rgba(255,255,255,0.35))" }} />;
      })}
      {/* The verdict's colour on the glass */}
      <div style={{ position: "absolute", inset: 0, opacity: spill * 0.35, background: `radial-gradient(ellipse at 50% ${(dashY / H) * 100}%, ${spillC} 0%, transparent 60%)`, mixBlendMode: "screen" }} />
      {/* A-pillars */}
      <svg width={W} height={H} style={{ position: "absolute", inset: 0 }}>
        <path d={`M0 0 L${W * 0.1} 0 L0 ${H * 0.55} Z`} fill="#000" />
        <path d={`M${W} 0 L${W * 0.9} 0 L${W} ${H * 0.55} Z`} fill="#000" />
      </svg>
      {/* The dash */}
      <svg width={W} height={H} style={{ position: "absolute", inset: 0 }}>
        <defs>
          <linearGradient id="dash" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#0d0d0d" />
            <stop offset="1" stopColor="#000" />
          </linearGradient>
        </defs>
        <path d={`M0 ${dashY + H * 0.05} Q${W / 2} ${dashY - H * 0.03} ${W} ${dashY + H * 0.05} L${W} ${H} L0 ${H} Z`} fill="url(#dash)" />
        <path d={`M0 ${dashY + H * 0.05} Q${W / 2} ${dashY - H * 0.03} ${W} ${dashY + H * 0.05}`} fill="none" stroke={spill > 0 ? spillC : "#fff"} strokeOpacity={0.1 + spill * 0.45} strokeWidth={3} />
      </svg>
      {/* Light sweeping through the car */}
      <div style={{ position: "absolute", inset: 0, background: "linear-gradient(170deg, transparent 30%, rgba(255,255,255,0.12) 50%, transparent 70%)", opacity: sweep }} />
    </div>
  );
};
