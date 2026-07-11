import { ReactNode, useState } from "react";
import { demurrage, eoq, fmt, inventoryTurns, safetyStock } from "../scm";

function NumField({
  label,
  value,
  onChange,
  suffix,
  step,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  suffix?: string;
  step?: string;
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-[11px] font-medium text-[#8A8F98]">{label}</span>
      <div className="relative">
        <input
          type="number"
          className="input py-2 pr-14"
          value={value}
          min="0"
          step={step}
          onChange={(e) => onChange(e.target.value)}
        />
        {suffix && (
          <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-[#8A8F98]">
            {suffix}
          </span>
        )}
      </div>
    </label>
  );
}

function Result({ label, value, unit, primary }: { label: string; value: string; unit?: string; primary?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-3 py-0.5">
      <span className="text-xs text-[#8A8F98]">{label}</span>
      <span className={primary ? "text-lg font-bold text-accent" : "text-sm font-semibold text-white"}>
        {value}
        {unit && <span className="ml-1 text-[10px] font-normal text-[#8A8F98]">{unit}</span>}
      </span>
    </div>
  );
}

function CalcCard({
  emoji,
  title,
  desc,
  formula,
  inputs,
  results,
}: {
  emoji: string;
  title: string;
  desc: string;
  formula: string;
  inputs: ReactNode;
  results: ReactNode;
}) {
  return (
    <div className="card flex flex-col gap-4">
      <div>
        <h3 className="text-sm font-bold text-white">
          {emoji} {title}
        </h3>
        <p className="meta mt-0.5 text-[11px] leading-relaxed">{desc}</p>
      </div>
      <div className="grid grid-cols-2 gap-3">{inputs}</div>
      <div className="rounded-xl border border-accent/15 bg-accent/[0.04] p-3">{results}</div>
      <p className="font-mono text-[10px] leading-relaxed text-[#8A8F98]/60">{formula}</p>
    </div>
  );
}

const num = (s: string) => (s.trim() === "" ? 0 : Number(s)) || 0;

function SafetyStockCalc() {
  const [sl, setSl] = useState("95");
  const [d, setD] = useState("100");
  const [sd, setSd] = useState("20");
  const [lt, setLt] = useState("7");
  const [ltSd, setLtSd] = useState("0");
  const r = safetyStock({
    serviceLevelPct: num(sl),
    avgDemand: num(d),
    demandStd: num(sd),
    leadTime: num(lt),
    leadTimeStd: num(ltSd),
  });
  return (
    <CalcCard
      emoji="🛡️"
      title="Safety Stock & Reorder Point"
      desc="Buffer stock and reorder trigger for a target service level. Add lead-time std dev for variable lead times."
      formula="SS = z · √(L·σd² + d̄²·σL²)   |   ROP = d̄·L + SS"
      inputs={
        <>
          <NumField label="Service level" value={sl} onChange={setSl} suffix="%" />
          <NumField label="Avg daily demand" value={d} onChange={setD} suffix="u/day" />
          <NumField label="Demand std dev" value={sd} onChange={setSd} suffix="u/day" />
          <NumField label="Lead time" value={lt} onChange={setLt} suffix="days" />
          <NumField label="Lead-time std dev" value={ltSd} onChange={setLtSd} suffix="days" />
        </>
      }
      results={
        <>
          <Result label="Z-score" value={fmt(r.z, 2)} />
          <Result label="σ over lead time" value={fmt(r.sigmaDLT, 1)} unit="units" />
          <Result label="Safety stock" value={fmt(r.safetyStock, 0)} unit="units" primary />
          <Result label="Reorder point" value={fmt(r.reorderPoint, 0)} unit="units" primary />
        </>
      }
    />
  );
}

function EoqCalc() {
  const [d, setD] = useState("12000");
  const [s, setS] = useState("500");
  const [h, setH] = useState("20");
  const r = eoq({ annualDemand: num(d), orderCost: num(s), holdingCostPerUnitYear: num(h) });
  return (
    <CalcCard
      emoji="📦"
      title="Economic Order Quantity"
      desc="The order size that minimises combined ordering + holding cost."
      formula="EOQ = √(2·D·S / H)   |   Total = (D/Q)·S + (Q/2)·H"
      inputs={
        <>
          <NumField label="Annual demand" value={d} onChange={setD} suffix="u/yr" />
          <NumField label="Cost per order" value={s} onChange={setS} suffix="$" />
          <NumField label="Holding cost" value={h} onChange={setH} suffix="$/u/yr" />
        </>
      }
      results={
        <>
          <Result label="Order quantity (EOQ)" value={fmt(r.eoq, 0)} unit="units" primary />
          <Result label="Orders per year" value={fmt(r.ordersPerYear, 1)} />
          <Result label="Order cycle" value={fmt(r.cycleDays, 0)} unit="days" />
          <Result label="Total annual cost" value={fmt(r.totalAnnualCost, 0)} primary />
        </>
      }
    />
  );
}

function DemurrageCalc() {
  const [c, setC] = useState("1");
  const [free, setFree] = useState("5");
  const [held, setHeld] = useState("12");
  const [rate, setRate] = useState("75");
  const r = demurrage({ containers: num(c), freeDays: num(free), daysHeld: num(held), perDiemRate: num(rate) });
  return (
    <CalcCard
      emoji="⏱️"
      title="Demurrage / Detention Estimator"
      desc="Chargeable days and cost once free time runs out. Works for demurrage (at port) or detention (off-port) — run it for each."
      formula="Chargeable = max(0, days held − free days)   |   Charge = chargeable · rate · containers"
      inputs={
        <>
          <NumField label="Containers" value={c} onChange={setC} />
          <NumField label="Free days" value={free} onChange={setFree} suffix="days" />
          <NumField label="Total days held" value={held} onChange={setHeld} suffix="days" />
          <NumField label="Per-diem rate" value={rate} onChange={setRate} suffix="$/day" />
        </>
      }
      results={
        <>
          <Result label="Chargeable days" value={fmt(r.chargeableDays, 0)} unit="days" />
          <Result label="Per container" value={fmt(r.perContainer, 0)} />
          <Result label="Total charge" value={fmt(r.totalCharge, 0)} primary />
        </>
      }
    />
  );
}

function TurnsCalc() {
  const [cogs, setCogs] = useState("2400000");
  const [inv, setInv] = useState("300000");
  const r = inventoryTurns({ annualCogs: num(cogs), avgInventoryValue: num(inv) });
  return (
    <CalcCard
      emoji="🔄"
      title="Inventory Turns & Days of Supply"
      desc="How fast inventory converts to sales, and how many days of stock you hold."
      formula="Turns = COGS / avg inventory   |   Days of supply = 365 / turns"
      inputs={
        <>
          <NumField label="Annual COGS" value={cogs} onChange={setCogs} suffix="$" />
          <NumField label="Avg inventory value" value={inv} onChange={setInv} suffix="$" />
        </>
      }
      results={
        <>
          <Result label="Inventory turns" value={fmt(r.turns, 1)} unit="× / yr" primary />
          <Result label="Days of supply" value={fmt(r.daysOfSupply, 0)} unit="days" primary />
        </>
      }
    />
  );
}

export default function Calculators() {
  return (
    <div>
      <div className="grid gap-4 sm:grid-cols-2">
        <SafetyStockCalc />
        <EoqCalc />
        <DemurrageCalc />
        <TurnsCalc />
      </div>
      <p className="meta mt-4 text-center text-[11px]">
        Everything runs in your browser — nothing is stored or sent. Estimates only; validate against your own contracts and data.
      </p>
    </div>
  );
}
