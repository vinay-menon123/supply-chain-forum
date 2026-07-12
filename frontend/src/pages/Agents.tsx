import { useEffect, useState } from "react";
import { api } from "../api";
import type {
  AgentFactor,
  AgentOption,
  AgentReport,
  AgentRun,
  ChannelFill,
  DecisionBrief,
  ErpShipment,
  ErpSnapshot,
  Evidence,
  FactorImpact,
  FactorSource,
  FulfilmentPlan,
  SignalView,
  Stakeholder,
} from "../types";

const fmtInr = (n: number) => "₹" + Math.round(n).toLocaleString("en-IN");
const fmtNum = (n: number) => Math.round(n).toLocaleString("en-IN");

const fillTone = (pct: number) =>
  pct >= 100
    ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
    : pct >= 60
    ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
    : pct > 0
    ? "border-orange-500/30 bg-orange-500/10 text-orange-300"
    : "border-rose-500/30 bg-rose-500/10 text-rose-300";

const urgencyTone = (u: string) =>
  u === "IMMEDIATE"
    ? "border-rose-500/30 bg-rose-500/10 text-rose-300"
    : u === "NEXT_CYCLE"
    ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
    : "border-white/10 bg-white/5 text-[#8A8F98]";

const priorityChip = (p: string) =>
  p === "CRITICAL"
    ? "border-rose-500/30 bg-rose-500/10 text-rose-300"
    : p === "HIGH"
    ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
    : "border-white/10 bg-white/5 text-[#8A8F98]";

const statusChip = (s: string) =>
  s === "BREAKDOWN"
    ? "border-rose-500/30 bg-rose-500/10 text-rose-300"
    : s === "IDLE"
    ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
    : "border-sky-500/30 bg-sky-500/10 text-sky-300";

const impactDot: Record<FactorImpact, string> = {
  INFO: "bg-white/30",
  GOOD: "bg-emerald-400",
  CAUTION: "bg-amber-400",
  CRITICAL: "bg-rose-400",
};

const sourceBadge: Record<FactorSource, string> = {
  LIVE: "border-emerald-500/30 bg-emerald-500/10 text-emerald-300",
  COMPUTED: "border-sky-500/30 bg-sky-500/10 text-sky-300",
  MODELED: "border-white/10 bg-white/5 text-[#8A8F98]",
  ROADMAP: "border-white/10 border-dashed bg-transparent text-[#5A6270]",
};

const optionTypeLabel: Record<string, string> = {
  EXTERNAL_CARRIER: "External carrier",
  OWN_FLEET: "Own fleet",
  REPAIR: "On-site repair",
  ROUTE_CHANGE: "Reroute",
  MODE_RAIL: "Mode-shift · rail",
  MODE_AIR: "Mode-shift · air",
  ALT_DC: "Alt-DC re-fulfill",
  REPLACEMENT_TRANSPORTER: "Replacement transporter",
  RDC_NEAR: "Nearest depot",
  RDC_POOL: "Depot network",
  HYBRID: "Depot + vehicle (hybrid)",
};

function SourceTag({ source }: { source: FactorSource }) {
  return (
    <span className={`badge ${sourceBadge[source]} px-1.5 py-0 text-[9px] tracking-wide`}>{source}</span>
  );
}

function ShipmentCard({
  s,
  selected,
  onSelect,
}: {
  s: ErpShipment;
  selected: boolean;
  onSelect: () => void;
}) {
  const disrupted = !!s.breakdownIssue;
  return (
    <button
      onClick={onSelect}
      className={`card card-lift w-full text-left transition ${
        selected ? "border-accent/60 shadow-[0_0_0_1px_rgba(94,106,210,0.5)]" : ""
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-bold text-white">{s.ref}</span>
            <span className={`badge ${priorityChip(s.priority)}`}>{s.priority}</span>
          </div>
          <p className="mt-0.5 text-xs text-[#8A8F98]">{s.cargo}</p>
        </div>
        {disrupted ? (
          <span className="flex-none text-lg" title="Disrupted">
            ⚠️
          </span>
        ) : (
          <span className="flex-none text-lg" title="Recovery review">
            🩺
          </span>
        )}
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px] text-[#8A8F98]">
        <span className="font-medium text-white/80">{s.route.replace("-", " → ")}</span>
        <span>· {Math.round(s.remainingKm)} km</span>
        <span>· SLA {Math.round(s.slaHoursRemaining)}h</span>
        <span>· {fmtInr(s.value)}</span>
      </div>
      {disrupted && (
        <p className="mt-2 rounded-lg border border-rose-500/15 bg-rose-500/[0.06] px-2.5 py-1.5 text-[11px] text-rose-300">
          {s.vehicleId} down — {s.breakdownIssue}
        </p>
      )}
    </button>
  );
}

function SignalStrip({ signals }: { signals: SignalView[] }) {
  return (
    <div className="mb-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
      {signals.map((sig, i) => (
        <div
          key={i}
          className={`card flex items-start gap-2.5 !p-3 ${
            sig.impact === "CRITICAL"
              ? "border-rose-500/25 bg-rose-500/[0.04]"
              : sig.impact === "CAUTION"
              ? "border-amber-500/20 bg-amber-500/[0.03]"
              : ""
          }`}
        >
          <span className={`mt-1 h-2 w-2 flex-none rounded-full ${impactDot[sig.impact]}`} />
          <div className="min-w-0">
            <div className="flex items-center gap-1.5">
              <p className="truncate text-[11px] font-semibold text-white/90">{sig.label}</p>
              <SourceTag source={sig.source} />
            </div>
            <p className="mt-0.5 text-[11px] leading-snug text-[#8A8F98]">{sig.detail}</p>
          </div>
        </div>
      ))}
    </div>
  );
}

function FactorList({ factors }: { factors: AgentFactor[] }) {
  return (
    <ul className="mt-2 grid gap-1.5 sm:grid-cols-2">
      {factors.map((f) => (
        <li key={f.code} className="flex items-start gap-2">
          <span className={`mt-1.5 h-1.5 w-1.5 flex-none rounded-full ${impactDot[f.impact]}`} />
          <span className="min-w-0 text-[11px] leading-snug text-[#8A8F98]">
            <span className="text-white/70">{f.label}:</span> {f.value}{" "}
            <SourceTag source={f.source} />
          </span>
        </li>
      ))}
    </ul>
  );
}

function AgentTraceItem({
  a,
  state,
}: {
  a: AgentReport;
  state: "done" | "active" | "pending";
}) {
  return (
    <div
      className={`card flex gap-3 transition-all duration-500 ${
        state === "pending" ? "opacity-40" : "opacity-100"
      }`}
    >
      <div className="flex-none">
        <span className="grid h-9 w-9 place-items-center rounded-xl border border-white/10 bg-white/[0.03] text-lg">
          {a.emoji}
        </span>
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-bold text-white">{a.name}</span>
          <span className="badge border-white/10 bg-white/5 text-[10px] text-[#8A8F98]">{a.domain}</span>
          {state === "done" ? (
            <span className="text-xs text-emerald-400">✓</span>
          ) : state === "active" ? (
            <span className="h-3 w-3 animate-spin rounded-full border-2 border-accent border-t-transparent" />
          ) : null}
        </div>
        <p className="text-[11px] text-[#8A8F98]">{a.role}</p>
        {state === "done" && (
          <>
            <p className="mt-1.5 text-sm leading-relaxed text-white/85">{a.headline}</p>
            {a.factors.length > 0 && <FactorList factors={a.factors} />}
          </>
        )}
      </div>
    </div>
  );
}

function ScoreBar({ score, recommended }: { score: number; recommended: boolean }) {
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-white/[0.06]">
      <div
        className={`h-full rounded-full ${recommended ? "bg-accent" : "bg-white/25"}`}
        style={{ width: `${Math.max(6, Math.min(100, score))}%` }}
      />
    </div>
  );
}

function OptionCard({ o, rank }: { o: AgentOption; rank: number }) {
  const [showCost, setShowCost] = useState(o.recommended);
  return (
    <div
      className={`card flex flex-col gap-3 ${
        o.recommended ? "border-accent/50 bg-accent/[0.05] shadow-[0_0_24px_rgba(94,106,210,0.12)]" : ""
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="grid h-6 w-6 flex-none place-items-center rounded-md bg-white/[0.06] text-xs font-bold text-white">
              {rank}
            </span>
            <h3 className="break-words text-sm font-bold text-white">{o.title}</h3>
            {o.recommended && (
              <span className="badge border-accent/40 bg-accent/20 text-accent">✓ Recommended</span>
            )}
          </div>
          <p className="mt-1 text-[11px] text-[#8A8F98]">
            {optionTypeLabel[o.type] ?? o.type} · {o.provider}
          </p>
        </div>
        <div className="flex-none text-right">
          <div className="text-lg font-bold text-white">{o.score}</div>
          <div className="text-[10px] text-[#8A8F98]">score</div>
        </div>
      </div>

      <ScoreBar score={o.score} recommended={o.recommended} />

      <div className="grid grid-cols-3 gap-2 text-center">
        <div className="rounded-lg border border-white/[0.06] bg-white/[0.02] py-1.5">
          <div className="text-sm font-semibold text-white">{o.etaHours}h</div>
          <div className={`text-[10px] ${o.onTime ? "text-emerald-400" : "text-rose-400"}`}>
            {o.plan ? `${o.onTimeProbPct}% fill` : `${o.onTimeProbPct}% on-time`}
          </div>
        </div>
        <div className="rounded-lg border border-white/[0.06] bg-white/[0.02] py-1.5">
          <div className="text-sm font-semibold text-white">{o.reliabilityPct}%</div>
          <div className="text-[10px] text-[#8A8F98]">reliability</div>
        </div>
        <div className="rounded-lg border border-white/[0.06] bg-white/[0.02] py-1.5">
          <div className="text-sm font-semibold text-white">{Math.round(o.co2Kg)}</div>
          <div className="text-[10px] text-[#8A8F98]">kg CO₂</div>
        </div>
      </div>

      {/* Channel fill — the distributor's answer: who actually gets served */}
      {o.plan && (
        <div className="rounded-lg border border-white/[0.06] bg-white/[0.02] p-2.5">
          <p className="mb-1.5 text-[10px] uppercase tracking-wider text-[#5A6270]">
            Channel fill inside promise window
          </p>
          <ChannelChips channels={o.plan.channelTotals} />
          {o.plan.unfilledUnits > 0 && (
            <p className="mt-1.5 text-[11px] text-rose-300">
              {fmtNum(o.plan.unfilledUnits)} units roll to the next replenishment
            </p>
          )}
        </div>
      )}

      {o.summary && (
        <p className="rounded-lg border border-white/[0.06] bg-white/[0.02] p-2.5 text-[11px] leading-relaxed text-[#8A8F98]">
          {o.summary}
        </p>
      )}

      {/* Expected landed cost + breakdown */}
      <div className="rounded-lg border border-white/[0.06] bg-white/[0.02] p-2.5">
        <button
          onClick={() => setShowCost((v) => !v)}
          className="flex w-full items-center justify-between gap-2 text-left"
        >
          <span className="text-[11px] text-[#8A8F98]">Expected landed cost</span>
          <span className="flex items-center gap-1.5 text-sm font-bold text-white">
            {fmtInr(o.expectedCost)}
            <span className="text-[10px] text-[#8A8F98]">{showCost ? "▲" : "▼"}</span>
          </span>
        </button>
        {showCost && (
          <ul className="mt-2 space-y-1 border-t border-white/[0.06] pt-2">
            {o.costBreakdown.map((c, i) => (
              <li key={i} className="flex items-center justify-between text-[11px] text-[#8A8F98]">
                <span>{c.label}</span>
                <span className="tabular-nums text-white/80">{fmtInr(c.amount)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {(o.pros.length > 0 || o.risks.length > 0) && (
        <div className="space-y-1">
          {o.pros.slice(0, 3).map((p, i) => (
            <div key={`p${i}`} className="flex gap-1.5 text-[11px] leading-snug text-[#8A8F98]">
              <span className="flex-none text-emerald-400/80">＋</span>
              <span>{p}</span>
            </div>
          ))}
          {o.risks.slice(0, 4).map((r, i) => (
            <div key={`r${i}`} className="flex gap-1.5 text-[11px] leading-snug text-[#8A8F98]">
              <span className="flex-none text-amber-400/80">▸</span>
              <span>{r}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/** Per-channel fill chips — the "who gets served" answer at a glance. */
function ChannelChips({ channels }: { channels: ChannelFill[] }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {channels.map((c) => (
        <span
          key={c.channel}
          title={`${c.channelName}: ${fmtNum(c.onTimeUnits)}/${fmtNum(c.demand)} units inside a ${c.promiseHrs}h promise`}
          className={`badge ${fillTone(c.fillPct)} text-[10px]`}
        >
          {c.channel} {c.fillPct}%
        </span>
      ))}
    </div>
  );
}

function PlanDetail({ plan }: { plan: FulfilmentPlan }) {
  return (
    <div className="space-y-4">
      {/* Sources */}
      <div className="card !p-0 overflow-hidden">
        <p className="border-b border-white/[0.06] px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">
          Where each city's units come from
        </p>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[520px] text-[11px]">
            <thead className="text-[#5A6270]">
              <tr className="border-b border-white/[0.04]">
                <th className="px-4 py-2 text-left font-medium">Source</th>
                <th className="px-4 py-2 text-left font-medium">To</th>
                <th className="px-4 py-2 text-right font-medium">Units</th>
                <th className="px-4 py-2 text-right font-medium">ETA</th>
                <th className="px-4 py-2 text-right font-medium">Freight</th>
              </tr>
            </thead>
            <tbody>
              {plan.sources.map((s, i) => (
                <tr key={i} className="border-b border-white/[0.03] last:border-0">
                  <td className="px-4 py-2">
                    <span className={`badge ${s.kind === "RDC" ? "border-sky-500/30 bg-sky-500/10 text-sky-300" : "border-white/10 bg-white/5 text-[#8A8F98]"} mr-1.5 text-[9px]`}>
                      {s.kind}
                    </span>
                    <span className="text-white/85">{s.source}</span>
                  </td>
                  <td className="px-4 py-2 text-[#8A8F98]">{s.city}</td>
                  <td className="px-4 py-2 text-right tabular-nums text-white/85">{fmtNum(s.units)}</td>
                  <td className="px-4 py-2 text-right tabular-nums text-[#8A8F98]">{s.etaHrs}h</td>
                  <td className="px-4 py-2 text-right tabular-nums text-[#8A8F98]">{fmtInr(s.freightInr)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Channel fill per city */}
      <div className="grid gap-4 md:grid-cols-2">
        {plan.cities.map((city) => (
          <div key={city.city} className="card !p-0 overflow-hidden">
            <div className="flex items-center justify-between border-b border-white/[0.06] px-4 py-2.5">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">{city.city}</p>
              <span className={`badge ${fillTone(city.fillPct)} text-[10px]`}>
                {city.fillPct}% in window
              </span>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[380px] text-[11px]">
                <thead className="text-[#5A6270]">
                  <tr className="border-b border-white/[0.04]">
                    <th className="px-3 py-2 text-left font-medium">Channel</th>
                    <th className="px-3 py-2 text-right font-medium">Demand</th>
                    <th className="px-3 py-2 text-right font-medium">Served</th>
                    <th className="px-3 py-2 text-right font-medium">Fill</th>
                    <th className="px-3 py-2 text-right font-medium">ETA / promise</th>
                  </tr>
                </thead>
                <tbody>
                  {city.channels.map((c) => (
                    <tr key={c.channel} className="border-b border-white/[0.03] last:border-0">
                      <td className="px-3 py-2 text-white/85">
                        {c.channel} <span className="text-[#5A6270]">{c.channelName}</span>
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums text-[#8A8F98]">{fmtNum(c.demand)}</td>
                      <td className="px-3 py-2 text-right tabular-nums text-white/85">{fmtNum(c.onTimeUnits)}</td>
                      <td className="px-3 py-2 text-right">
                        <span className={`badge ${fillTone(c.fillPct)} text-[10px]`}>{c.fillPct}%</span>
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums text-[#8A8F98]">
                        {c.etaHrs}h / {c.promiseHrs}h
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ))}
      </div>

      {/* Per-SKU + backfill */}
      <div className="grid gap-4 md:grid-cols-2">
        <div className="card !p-0 overflow-hidden">
          <p className="border-b border-white/[0.06] px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">
            Per-SKU sourcing split
          </p>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[360px] text-[11px]">
              <thead className="text-[#5A6270]">
                <tr className="border-b border-white/[0.04]">
                  <th className="px-3 py-2 text-left font-medium">SKU</th>
                  <th className="px-3 py-2 text-right font-medium">Demand</th>
                  <th className="px-3 py-2 text-right font-medium">From depot</th>
                  <th className="px-3 py-2 text-right font-medium">From vehicle</th>
                  <th className="px-3 py-2 text-right font-medium">Short</th>
                </tr>
              </thead>
              <tbody>
                {plan.skus.map((s) => (
                  <tr key={s.sku} className="border-b border-white/[0.03] last:border-0">
                    <td className="px-3 py-2 text-white/85" title={s.name}>{s.sku}</td>
                    <td className="px-3 py-2 text-right tabular-nums text-[#8A8F98]">{fmtNum(s.demand)}</td>
                    <td className="px-3 py-2 text-right tabular-nums text-sky-300">{fmtNum(s.fromRdc)}</td>
                    <td className="px-3 py-2 text-right tabular-nums text-white/70">{fmtNum(s.fromTruck)}</td>
                    <td className={`px-3 py-2 text-right tabular-nums ${s.unfilled > 0 ? "text-rose-300" : "text-[#5A6270]"}`}>
                      {fmtNum(s.unfilled)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="card !p-0 overflow-hidden">
          <p className="border-b border-white/[0.06] px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">
            Depot draw-down &amp; backfill
          </p>
          {plan.backfills.length === 0 ? (
            <p className="px-4 py-3 text-[11px] text-[#8A8F98]">No depot stock used — nothing to backfill.</p>
          ) : (
            <ul>
              {plan.backfills.map((b, i) => (
                <li key={i} className="border-b border-white/[0.04] px-4 py-2.5 last:border-0">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-[11px] font-medium text-white/85">{b.rdc}</span>
                    <span
                      className={`badge ${
                        b.unitsRepaid >= b.unitsLent
                          ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                          : "border-amber-500/30 bg-amber-500/10 text-amber-300"
                      } text-[10px]`}
                    >
                      lent {fmtNum(b.unitsLent)} · repaid {fmtNum(b.unitsRepaid)}
                    </span>
                  </div>
                  <p className="mt-1 text-[11px] leading-snug text-[#8A8F98]">{b.note}</p>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}

function StakeholderPanel({ stakeholders }: { stakeholders: Stakeholder[] }) {
  const order = { IMMEDIATE: 0, NEXT_CYCLE: 1, FYI: 2 } as Record<string, number>;
  const sorted = [...stakeholders].sort((a, b) => (order[a.urgency] ?? 3) - (order[b.urgency] ?? 3));
  return (
    <div className="card !p-0 overflow-hidden">
      <p className="border-b border-white/[0.06] px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">
        Who to tell — {stakeholders.length} departments
      </p>
      <ul>
        {sorted.map((s, i) => (
          <li
            key={i}
            className="flex flex-wrap items-start justify-between gap-2 border-b border-white/[0.04] px-4 py-2.5 last:border-0"
          >
            <div className="min-w-0">
              <p className="text-[11px] font-medium text-white/85">{s.department}</p>
              <p className="mt-0.5 text-[11px] leading-snug text-[#8A8F98]">{s.action}</p>
            </div>
            <span className={`badge ${urgencyTone(s.urgency)} flex-none text-[10px]`}>
              {s.urgency.replace("_", " ")}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** The analyst's audit: how the engine actually got to its answer. */
function DecisionRationale({ brief }: { brief: DecisionBrief }) {
  const Block = ({
    title,
    tone,
    items,
  }: {
    title: string;
    tone: string;
    items: string[];
  }) =>
    items.length === 0 ? null : (
      <div>
        <p className={`mb-1.5 text-[11px] font-semibold uppercase tracking-wider ${tone}`}>{title}</p>
        <ul className="space-y-1.5">
          {items.map((t, i) => (
            <li key={i} className="flex gap-2 text-[12px] leading-relaxed text-[#8A8F98]">
              <span className="flex-none text-[#5A6270]">•</span>
              <span>{t}</span>
            </li>
          ))}
        </ul>
      </div>
    );

  return (
    <div className="space-y-5">
      {/* Bottom line first — the thing they'd ask for if they read nothing else */}
      <div className="rounded-2xl border border-accent/30 bg-accent/[0.06] p-4">
        <p className="text-xs font-semibold uppercase tracking-wider text-accent">The call, in one paragraph</p>
        <p className="mt-1.5 text-sm leading-relaxed text-white/90">{brief.bottomLine}</p>
      </div>

      <div className="card">
        <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">Situation</p>
        <p className="text-[12px] leading-relaxed text-white/80">{brief.situation}</p>
      </div>

      {/* The reasoning chain */}
      {brief.howWeGotHere.length > 0 && (
        <div className="card">
          <p className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">
            How we got here — the reasoning chain
          </p>
          <ol className="space-y-3">
            {brief.howWeGotHere.map((s) => (
              <li key={s.step} className="flex gap-3">
                <span className="grid h-6 w-6 flex-none place-items-center rounded-md border border-white/10 bg-white/[0.04] text-[11px] font-bold text-accent">
                  {s.step}
                </span>
                <div className="min-w-0">
                  <p className="text-[12px] font-semibold text-white/90">{s.question}</p>
                  <p className="mt-0.5 text-[12px] leading-relaxed text-[#8A8F98]">{s.finding}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      )}

      {/* Head to head */}
      {brief.headToHead.length > 0 && (
        <div className="card">
          <p className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">
            Why not the others — head to head
          </p>
          <div className="space-y-2.5">
            {brief.headToHead.map((c) => (
              <div key={c.optionId} className="rounded-lg border border-white/[0.06] bg-white/[0.02] p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-[12px] font-semibold text-white/90">{c.title}</span>
                  <div className="flex items-center gap-2">
                    <span className={`badge ${fillTone(c.servicePct)} text-[10px]`}>{c.servicePct}% served</span>
                    <span
                      className={`badge text-[10px] ${
                        c.deltaVsBest >= 0
                          ? "border-rose-500/30 bg-rose-500/10 text-rose-300"
                          : "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                      }`}
                    >
                      {c.deltaVsBest >= 0 ? "+" : "−"}
                      {fmtInr(Math.abs(c.deltaVsBest))}
                    </span>
                  </div>
                </div>
                <p className="mt-1.5 text-[12px] leading-relaxed text-[#8A8F98]">{c.whyNotChosen}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        <div className="card space-y-4">
          <Block title="What tipped it" tone="text-emerald-300" items={brief.decisiveFactors} />
          <Block title="What bound the decision" tone="text-amber-300" items={brief.bindingConstraints} />
        </div>
        <div className="card space-y-4">
          <Block title="What would change the answer" tone="text-sky-300" items={brief.whatWouldChangeIt} />
          <Block title="Assumptions (tunable)" tone="text-[#8A8F98]" items={brief.assumptions} />
          <Block title="Not considered — honest limits" tone="text-[#5A6270]" items={brief.notConsidered} />
        </div>
      </div>
    </div>
  );
}

function EvidenceTrail({ evidence }: { evidence: Evidence[] }) {
  return (
    <div className="card !p-0 overflow-hidden">
      <p className="border-b border-white/[0.06] px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">
        Evidence trail — what drove the call
      </p>
      <ul>
        {evidence.map((e, i) => (
          <li
            key={i}
            className="flex items-center justify-between gap-3 border-b border-white/[0.04] px-4 py-2 last:border-0"
          >
            <span className="flex items-center gap-2 text-[11px] text-[#8A8F98]">
              {e.label}
              <SourceTag source={e.source} />
            </span>
            <span className="text-right text-[11px] font-medium text-white/85">{e.value}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default function Agents() {
  const [snapshot, setSnapshot] = useState<ErpSnapshot | null>(null);
  const [aiEnabled, setAiEnabled] = useState(false);
  const [aiProvider, setAiProvider] = useState("none");
  const [showErp, setShowErp] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [disruptionText, setDisruptionText] = useState("");
  const [running, setRunning] = useState(false);
  const [run, setRun] = useState<AgentRun | null>(null);
  const [revealed, setRevealed] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    api<{ erp: ErpSnapshot; aiEnabled: boolean; aiProvider: string }>("/agents/erp")
      .then((d) => {
        setSnapshot(d.erp);
        setAiEnabled(d.aiEnabled);
        setAiProvider(d.aiProvider);
        const disrupted = d.erp.shipments.find((s) => s.breakdownIssue);
        setSelectedId((disrupted ?? d.erp.shipments[0])?.id ?? null);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load ERP data"));
  }, []);

  // Reveal the twelve agents one at a time for a "thinking" effect.
  useEffect(() => {
    if (!run) {
      setRevealed(0);
      return;
    }
    setRevealed(0);
    let i = 0;
    const id = setInterval(() => {
      i += 1;
      setRevealed(i);
      if (i >= run.agents.length) clearInterval(id);
    }, 360);
    return () => clearInterval(id);
  }, [run]);

  async function runScenario() {
    if (!selectedId) return;
    setRunning(true);
    setRun(null);
    setError("");
    try {
      const r = await api<AgentRun>("/agents/run", {
        method: "POST",
        body: JSON.stringify({ shipmentId: selectedId, disruption: disruptionText.trim() || undefined }),
      });
      setRun(r);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Agent run failed");
    } finally {
      setRunning(false);
    }
  }

  const selected = snapshot?.shipments.find((s) => s.id === selectedId) ?? null;
  const optionsReady = run && revealed >= run.agents.length;
  const recommendedPlan = run?.options.find((o) => o.recommended)?.plan ?? null;

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center gap-3">
        <h1 className="heading animate-fade-in-up">
          🤖 AI <span className="gradient-text">Control Tower</span>
        </h1>
        <span
          className={`badge ${
            aiEnabled
              ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
              : "border-white/10 bg-white/5 text-[#8A8F98]"
          }`}
        >
          {aiEnabled
            ? `AI-powered · ${aiProvider === "gemini" ? "Gemini" : aiProvider === "anthropic" ? "Claude" : "AI"}`
            : "Heuristic mode"}
        </span>
        <span className="badge border-white/10 bg-white/5 text-[#8A8F98]">
          {run ? run.factorsConsidered : 118}-factor model · 12 agents
        </span>
      </div>
      <p className="meta animate-fade-in-up mb-6 [animation-delay:100ms] max-w-2xl">
        Report a disruption and twelve specialist agents interrogate the ERP and live external signals —
        weather, the festival calendar, the e-way-bill clock — generate every viable recovery play, and rank
        them by <span className="text-white/80">risk-adjusted expected landed cost</span>. Every figure is
        computed from data; the agents only write the reasoning.
      </p>

      {error && <p className="card mb-4 text-sm text-red-400">{error}</p>}
      {!error && !snapshot && <p className="py-10 text-center text-[#8A8F98]">Loading ERP…</p>}

      {snapshot && (
        <>
          {/* Intake */}
          <div className="mb-4 flex items-end justify-between gap-3">
            <h2 className="text-sm font-semibold uppercase tracking-wider text-[#8A8F98]">
              1. Report a disruption
            </h2>
            <button onClick={() => setShowErp((v) => !v)} className="btn-secondary px-3 py-1.5 text-xs">
              {showErp ? "Hide" : "View"} ERP data
            </button>
          </div>

          {showErp && (
            <div className="card animate-fade-in mb-5 space-y-4 text-xs">
              <div>
                <p className="section-title mb-2">Fleet ({snapshot.vehicles.length})</p>
                <div className="flex flex-wrap gap-2">
                  {snapshot.vehicles.map((v) => (
                    <span key={v.id} className={`badge ${statusChip(v.status)}`}>
                      {v.id} · {v.currentCity} · {v.status}
                    </span>
                  ))}
                </div>
              </div>
              <div>
                <p className="section-title mb-2">Carriers ({snapshot.carriers.length})</p>
                <div className="flex flex-wrap gap-2">
                  {snapshot.carriers.map((c) => (
                    <span key={c.id} className="badge border-white/10 bg-white/[0.03] text-[#8A8F98]">
                      {c.name} · {c.reliabilityPct}% · ₹{c.costPerKm}/km
                    </span>
                  ))}
                </div>
              </div>
            </div>
          )}

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {snapshot.shipments.map((s) => (
              <ShipmentCard
                key={s.id}
                s={s}
                selected={s.id === selectedId}
                onSelect={() => setSelectedId(s.id)}
              />
            ))}
          </div>

          <div className="mt-3">
            <label className="mb-1.5 block text-[11px] text-[#8A8F98]">
              What happened? <span className="text-[#5A6270]">(optional — the agents factor in your note)</span>
            </label>
            <input
              value={disruptionText}
              onChange={(e) => setDisruptionText(e.target.value)}
              placeholder="e.g. driver reports gearbox seized on NH-44, load intact, rain starting"
              className="input w-full text-sm"
            />
          </div>

          {/* Run bar */}
          <div className="sticky bottom-4 z-10 mt-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-white/10 bg-bg-base/90 p-4 backdrop-blur-xl">
            <div className="min-w-0 text-sm">
              {selected ? (
                <>
                  <span className="text-[#8A8F98]">Selected:</span>{" "}
                  <span className="font-semibold text-white">{selected.ref}</span>{" "}
                  <span className="text-[#8A8F98]">— {selected.route.replace("-", " → ")}</span>
                </>
              ) : (
                <span className="text-[#8A8F98]">Pick a shipment to begin.</span>
              )}
            </div>
            <button onClick={runScenario} disabled={!selectedId || running} className="btn-primary">
              {running ? "Agents analysing…" : "▶ Run control tower"}
            </button>
          </div>

          {/* Run output */}
          {(running || run) && (
            <div className="mt-8">
              {running && !run && (
                <div className="card flex items-center gap-3 text-sm text-[#8A8F98]">
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-accent border-t-transparent" />
                  Orchestrator is gathering ERP data and pulling live signals…
                </div>
              )}

              {run && (
                <>
                  {/* Scenario banner */}
                  <div className="card mb-4 border-rose-500/20 bg-rose-500/[0.04]">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="badge border-rose-500/30 bg-rose-500/10 text-rose-300">
                        ⚠️ {run.scenario.disruption}
                      </span>
                      <span className={`badge ${priorityChip(run.scenario.priority)}`}>
                        {run.scenario.priority}
                      </span>
                      <span className="text-xs text-[#8A8F98]">
                        {run.scenario.ref} · {run.scenario.vehiclePlate} · {run.scenario.origin} →{" "}
                        {run.scenario.destination}
                      </span>
                    </div>
                    <p className="mt-2 text-sm text-white/90">{run.scenario.summary}</p>
                  </div>

                  {/* Live signals */}
                  {run.signals.length > 0 && (
                    <>
                      <h2 className="mb-2 text-sm font-semibold uppercase tracking-wider text-[#8A8F98]">
                        Live signals
                      </h2>
                      <SignalStrip signals={run.signals} />
                    </>
                  )}

                  {/* Agent trace */}
                  <h2 className="mb-3 mt-6 text-sm font-semibold uppercase tracking-wider text-[#8A8F98]">
                    2. Agent analysis{" "}
                    <span className="text-[#5A6270]">
                      ({Math.min(revealed, run.agents.length)}/{run.agents.length})
                    </span>
                  </h2>
                  <div className="space-y-2.5">
                    {run.agents.map((a, i) => (
                      <AgentTraceItem
                        key={a.name}
                        a={a}
                        state={i < revealed ? "done" : i === revealed ? "active" : "pending"}
                      />
                    ))}
                  </div>

                  {/* Options + recommendation */}
                  {optionsReady && (
                    <div className="animate-fade-in-up mt-8">
                      <div className="mb-4 flex flex-wrap items-center gap-2">
                        <h2 className="text-sm font-semibold uppercase tracking-wider text-[#8A8F98]">
                          3. Recovery options — your call
                        </h2>
                        <span className="badge border-white/10 bg-white/5 text-[#8A8F98]">
                          {run.aiPowered
                            ? `AI-narrated · ${
                                run.aiProvider === "gemini"
                                  ? "Gemini"
                                  : run.aiProvider === "anthropic"
                                  ? "Claude"
                                  : "AI"
                              }`
                            : "heuristic narration"}
                        </span>
                      </div>

                      <div className="mb-4 rounded-2xl border border-accent/30 bg-accent/[0.06] p-4">
                        <p className="text-xs font-semibold uppercase tracking-wider text-accent">
                          🧭 Recommendation
                        </p>
                        <p className="mt-1.5 text-sm leading-relaxed text-white/90">
                          {run.recommendation.rationale}
                        </p>
                      </div>

                      {run.recommendation.evidence.length > 0 && (
                        <div className="mb-5">
                          <EvidenceTrail evidence={run.recommendation.evidence} />
                        </div>
                      )}

                      <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
                        {run.options.map((o, i) => (
                          <OptionCard key={o.id} o={o} rank={i + 1} />
                        ))}
                      </div>

                      {/* Distribution plan for the recommended option */}
                      {recommendedPlan && (
                        <div className="mt-8">
                          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-[#8A8F98]">
                            4. Fulfilment plan —{" "}
                            <span className="text-white/80">{run.recommendation.title}</span>
                          </h2>
                          <PlanDetail plan={recommendedPlan} />
                        </div>
                      )}

                      {run.stakeholders.length > 0 && (
                        <div className="mt-8">
                          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-[#8A8F98]">
                            5. Notify the network
                          </h2>
                          <StakeholderPanel stakeholders={run.stakeholders} />
                        </div>
                      )}

                      {/* Decision rationale — the analyst's audit of how we got here */}
                      {run.brief && (
                        <div className="mt-10">
                          <h2 className="mb-1 text-sm font-semibold uppercase tracking-wider text-[#8A8F98]">
                            {run.stakeholders.length > 0 ? "6" : "4"}. Decision rationale
                          </h2>
                          <p className="meta mb-4 text-[11px]">
                            How the engine reached this call — the constraints that bound it, what it compared,
                            and what would change the answer.
                          </p>
                          <DecisionRationale brief={run.brief} />
                        </div>
                      )}

                      <p className="meta mt-6 text-center text-[11px]">
                        Decision-support only — a human approves the final move. ERP is simulated; weather,
                        festival calendar and e-way-bill clock are live.
                      </p>
                    </div>
                  )}
                </>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
