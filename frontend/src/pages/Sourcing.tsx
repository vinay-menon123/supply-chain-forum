import { useEffect, useState } from "react";
import { api } from "../api";
import type { MarketplaceLink, SourcingReport, VendorSource } from "../types";

const fmtInr = (n: number) => "₹" + Math.round(n).toLocaleString("en-IN");

const confidenceChip = (c: string) =>
  c === "HIGH"
    ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
    : c === "LOW"
    ? "border-white/10 bg-white/5 text-[#8A8F98]"
    : "border-amber-500/30 bg-amber-500/10 text-amber-300";

const marketplaceGlyph: Record<string, string> = {
  IndiaMART: "🏭",
  TradeIndia: "🌐",
  "Amazon Business": "📦",
  Alibaba: "🚢",
  "Google Shopping": "🔍",
};

function VendorCard({ v }: { v: VendorSource }) {
  return (
    <div className="card flex flex-col gap-2">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="text-sm font-bold text-white break-words">{v.name}</h3>
          <p className="mt-0.5 text-[11px] text-[#8A8F98]">{v.type}</p>
        </div>
        <span className={`badge ${confidenceChip(v.confidence)} flex-none text-[10px]`}>
          {v.confidence} confidence
        </span>
      </div>
      {v.priceRange && (
        <div className="rounded-lg border border-white/[0.06] bg-white/[0.02] px-2.5 py-1.5">
          <span className="text-[10px] uppercase tracking-wider text-[#5A6270]">Est. price</span>
          <p className="text-sm font-semibold text-white">{v.priceRange}</p>
        </div>
      )}
      <div className="grid grid-cols-2 gap-x-3 gap-y-1 text-[11px] text-[#8A8F98]">
        {v.regionCoverage && (
          <p>
            <span className="text-white/60">Region:</span> {v.regionCoverage}
          </p>
        )}
        {v.moq && (
          <p>
            <span className="text-white/60">MOQ:</span> {v.moq}
          </p>
        )}
        {v.leadTime && (
          <p>
            <span className="text-white/60">Lead time:</span> {v.leadTime}
          </p>
        )}
      </div>
      {v.note && <p className="text-[11px] leading-snug text-[#8A8F98]">{v.note}</p>}
    </div>
  );
}

function MarketplaceButton({ m }: { m: MarketplaceLink }) {
  return (
    <a
      href={m.url}
      target="_blank"
      rel="noopener noreferrer"
      className="card card-lift flex items-start gap-3 transition hover:border-accent/50"
    >
      <span className="grid h-9 w-9 flex-none place-items-center rounded-xl border border-white/10 bg-white/[0.03] text-lg">
        {marketplaceGlyph[m.marketplace] ?? "🔗"}
      </span>
      <div className="min-w-0">
        <div className="flex items-center gap-1.5">
          <span className="text-sm font-semibold text-white">{m.marketplace}</span>
          <span className="text-[11px] text-accent">↗</span>
        </div>
        <p className="mt-0.5 text-[11px] leading-snug text-[#8A8F98]">{m.note}</p>
      </div>
    </a>
  );
}

export default function Sourcing() {
  const [aiEnabled, setAiEnabled] = useState(false);
  const [aiProvider, setAiProvider] = useState("none");
  const [examples, setExamples] = useState<string[]>([]);
  const [defaultRegion, setDefaultRegion] = useState("India");

  const [requirement, setRequirement] = useState("");
  const [region, setRegion] = useState("India");
  const [quantity, setQuantity] = useState("");
  const [unit, setUnit] = useState("");

  const [running, setRunning] = useState(false);
  const [report, setReport] = useState<SourcingReport | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api<{ aiEnabled: boolean; aiProvider: string; defaultRegion: string; examples: string[] }>(
      "/sourcing/config"
    )
      .then((d) => {
        setAiEnabled(d.aiEnabled);
        setAiProvider(d.aiProvider);
        setExamples(d.examples ?? []);
        setDefaultRegion(d.defaultRegion ?? "India");
        setRegion(d.defaultRegion ?? "India");
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load"));
  }, []);

  async function analyze() {
    if (!requirement.trim()) {
      setError("Describe what you need to source");
      return;
    }
    setRunning(true);
    setError("");
    setReport(null);
    try {
      const r = await api<SourcingReport>("/sourcing/analyze", {
        method: "POST",
        body: JSON.stringify({
          requirement: requirement.trim(),
          region: region.trim() || defaultRegion,
          quantity: quantity.trim() || undefined,
          unit: unit.trim() || undefined,
        }),
      });
      setReport(r);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Sourcing analysis failed");
    } finally {
      setRunning(false);
    }
  }

  const band = report?.priceBand;
  const hasPrice = !!band && (band.low > 0 || band.typical > 0 || band.high > 0);

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center gap-3">
        <h1 className="heading animate-fade-in-up">
          🔎 Vendor <span className="gradient-text">Sourcing</span>
        </h1>
        <span
          className={`badge ${
            aiEnabled
              ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
              : "border-white/10 bg-white/5 text-[#8A8F98]"
          }`}
        >
          {aiEnabled
            ? `AI estimate · ${aiProvider === "gemini" ? "Gemini" : aiProvider === "anthropic" ? "Claude" : "AI"}`
            : "Links-only mode"}
        </span>
      </div>
      <p className="meta animate-fade-in-up mb-6 [animation-delay:100ms] max-w-2xl">
        Describe what you need to buy and where. You get <span className="text-white/80">live marketplace
        search links</span> (IndiaMART, TradeIndia, Amazon Business, Alibaba) plus an{" "}
        <span className="text-white/80">AI market-price estimate</span> with likely vendors, MOQ and lead
        times. The links are real and verifiable; the prices are indicative estimates — always confirm with a quote.
      </p>

      {error && <p className="card mb-4 text-sm text-red-400">{error}</p>}

      {/* Intake */}
      <div className="card space-y-3">
        <div>
          <label className="mb-1.5 block text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">
            What do you need to source?
          </label>
          <input
            value={requirement}
            onChange={(e) => setRequirement(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && analyze()}
            placeholder="e.g. Pallet racks for a 20,000 sq ft warehouse"
            className="input w-full text-sm"
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <div>
            <label className="mb-1.5 block text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">
              Region
            </label>
            <input
              value={region}
              onChange={(e) => setRegion(e.target.value)}
              placeholder="India / Pune / Maharashtra"
              className="input w-full text-sm"
            />
          </div>
          <div>
            <label className="mb-1.5 block text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">
              Quantity <span className="font-normal normal-case text-[#5A6270]">(optional)</span>
            </label>
            <input
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              placeholder="e.g. 50"
              className="input w-full text-sm"
            />
          </div>
          <div>
            <label className="mb-1.5 block text-[11px] font-semibold uppercase tracking-wider text-[#8A8F98]">
              Unit <span className="font-normal normal-case text-[#5A6270]">(optional)</span>
            </label>
            <input
              value={unit}
              onChange={(e) => setUnit(e.target.value)}
              placeholder="e.g. bays / units / tonnes"
              className="input w-full text-sm"
            />
          </div>
        </div>

        {examples.length > 0 && !report && (
          <div className="flex flex-wrap gap-2 pt-1">
            <span className="text-[11px] text-[#5A6270]">Try:</span>
            {examples.map((ex) => (
              <button
                key={ex}
                onClick={() => setRequirement(ex)}
                className="pill-inactive text-[11px]"
              >
                {ex}
              </button>
            ))}
          </div>
        )}

        <div className="flex justify-end pt-1">
          <button onClick={analyze} disabled={running || !requirement.trim()} className="btn-primary">
            {running ? "Searching the market…" : "🔎 Find vendors & prices"}
          </button>
        </div>
      </div>

      {running && !report && (
        <div className="card mt-5 flex items-center gap-3 text-sm text-[#8A8F98]">
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-accent border-t-transparent" />
          Building marketplace links and estimating the market…
        </div>
      )}

      {report && (
        <div className="animate-fade-in-up mt-6 space-y-6">
          {/* Header + data basis */}
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-lg font-bold text-white">{report.category}</h2>
              <span className="badge border-white/10 bg-white/5 text-[#8A8F98]">{report.region}</span>
              <span
                className={`badge ${
                  report.aiPowered
                    ? "border-sky-500/30 bg-sky-500/10 text-sky-300"
                    : "border-white/10 bg-white/5 text-[#8A8F98]"
                }`}
              >
                {report.aiPowered ? "AI estimate" : "links only"}
              </span>
            </div>
            {report.specSummary && (
              <p className="mt-1.5 text-sm leading-relaxed text-white/85">{report.specSummary}</p>
            )}
            <p className="mt-2 rounded-lg border border-amber-500/15 bg-amber-500/[0.05] px-3 py-2 text-[11px] leading-snug text-amber-200/80">
              ⓘ {report.dataBasis}
            </p>
          </div>

          {/* Price band */}
          {hasPrice && band && (
            <div>
              <h3 className="mb-2 text-sm font-semibold uppercase tracking-wider text-[#8A8F98]">
                Estimated price band <span className="text-[#5A6270]">({band.unit})</span>
              </h3>
              <div className="grid grid-cols-3 gap-3">
                <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4 text-center">
                  <p className="text-[11px] uppercase tracking-wider text-[#8A8F98]">Budget</p>
                  <p className="mt-1 text-xl font-bold tabular-nums text-white">{fmtInr(band.low)}</p>
                </div>
                <div className="rounded-2xl border border-accent/30 bg-accent/[0.06] p-4 text-center">
                  <p className="text-[11px] uppercase tracking-wider text-accent/80">Typical</p>
                  <p className="mt-1 text-2xl font-bold tabular-nums text-accent">{fmtInr(band.typical)}</p>
                </div>
                <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4 text-center">
                  <p className="text-[11px] uppercase tracking-wider text-[#8A8F98]">Premium</p>
                  <p className="mt-1 text-xl font-bold tabular-nums text-white">{fmtInr(band.high)}</p>
                </div>
              </div>
            </div>
          )}

          {/* Marketplace links — the real, verifiable part */}
          <div>
            <h3 className="mb-2 text-sm font-semibold uppercase tracking-wider text-[#8A8F98]">
              Live marketplace searches{" "}
              <span className="font-normal normal-case text-[#5A6270]">— real listings, opens in a new tab</span>
            </h3>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {report.marketplaceLinks.map((m) => (
                <MarketplaceButton key={m.marketplace} m={m} />
              ))}
            </div>
          </div>

          {/* Vendors */}
          {report.vendors.length > 0 && (
            <div>
              <h3 className="mb-2 text-sm font-semibold uppercase tracking-wider text-[#8A8F98]">
                Likely vendors &amp; suppliers
              </h3>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {report.vendors.map((v, i) => (
                  <VendorCard key={i} v={v} />
                ))}
              </div>
            </div>
          )}

          {/* Cost drivers + tips */}
          {(report.costDrivers.length > 0 || report.buyingTips.length > 0) && (
            <div className="grid gap-4 md:grid-cols-2">
              {report.costDrivers.length > 0 && (
                <div className="card">
                  <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-amber-300">
                    What drives the price
                  </p>
                  <ul className="space-y-1.5">
                    {report.costDrivers.map((c, i) => (
                      <li key={i} className="flex gap-2 text-[12px] leading-relaxed text-[#8A8F98]">
                        <span className="flex-none text-amber-400/70">▸</span>
                        <span>{c}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {report.buyingTips.length > 0 && (
                <div className="card">
                  <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-emerald-300">
                    Buying tips
                  </p>
                  <ul className="space-y-1.5">
                    {report.buyingTips.map((t, i) => (
                      <li key={i} className="flex gap-2 text-[12px] leading-relaxed text-[#8A8F98]">
                        <span className="flex-none text-emerald-400/70">＋</span>
                        <span>{t}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}

          {/* Bottom line */}
          {report.summary && (
            <div className="rounded-2xl border border-accent/30 bg-accent/[0.06] p-4">
              <p className="text-xs font-semibold uppercase tracking-wider text-accent">Bottom line</p>
              <p className="mt-1.5 text-sm leading-relaxed text-white/90">{report.summary}</p>
            </div>
          )}

          <p className="meta text-center text-[11px]">
            Decision-support only. Prices are AI estimates, not live quotes — confirm with suppliers via the
            marketplace links before committing.
          </p>
        </div>
      )}
    </div>
  );
}
