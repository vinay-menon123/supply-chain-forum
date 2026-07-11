// Pure supply-chain math for the interactive calculators. No UI, no state —
// kept separate so the formulas stay easy to read and verify.

/**
 * Inverse standard normal CDF (quantile) — Peter Acklam's rational approximation.
 * Maps a service level p∈(0,1) to its z-score, e.g. normInv(0.95) ≈ 1.6449.
 */
export function normInv(p: number): number {
  if (p <= 0) return -Infinity;
  if (p >= 1) return Infinity;

  const a = [-3.969683028665376e1, 2.209460984245205e2, -2.759285104469687e2,
    1.38357751867269e2, -3.066479806614716e1, 2.506628277459239e0];
  const b = [-5.447609879822406e1, 1.615858368580409e2, -1.556989798598866e2,
    6.680131188771972e1, -1.328068155288572e1];
  const c = [-7.784894002430293e-3, -3.223964580411365e-1, -2.400758277161838e0,
    -2.549732539343734e0, 4.374664141464968e0, 2.938163982698783e0];
  const d = [7.784695709041462e-3, 3.224671290700398e-1, 2.445134137142996e0,
    3.754408661907416e0];

  const plow = 0.02425;
  const phigh = 1 - plow;

  if (p < plow) {
    const q = Math.sqrt(-2 * Math.log(p));
    return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
      ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
  }
  if (p <= phigh) {
    const q = p - 0.5;
    const r = q * q;
    return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
      (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
  }
  const q = Math.sqrt(-2 * Math.log(1 - p));
  return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
}

export interface SafetyStockResult {
  z: number;
  sigmaDLT: number;   // std dev of demand over lead time
  safetyStock: number;
  reorderPoint: number;
}

/**
 * Safety stock with demand AND (optional) lead-time variability:
 *   σ_DLT = √(L·σ_d² + d̄²·σ_L²),  SS = z·σ_DLT,  ROP = d̄·L + SS
 * Set leadTimeStd = 0 for the classic demand-only formula.
 */
export function safetyStock(input: {
  serviceLevelPct: number;
  avgDemand: number;
  demandStd: number;
  leadTime: number;
  leadTimeStd: number;
}): SafetyStockResult {
  const { serviceLevelPct, avgDemand, demandStd, leadTime, leadTimeStd } = input;
  const z = normInv(Math.min(Math.max(serviceLevelPct, 0), 99.999) / 100);
  const sigmaDLT = Math.sqrt(
    leadTime * demandStd * demandStd + avgDemand * avgDemand * leadTimeStd * leadTimeStd
  );
  const ss = z * sigmaDLT;
  return {
    z,
    sigmaDLT,
    safetyStock: ss,
    reorderPoint: avgDemand * leadTime + ss,
  };
}

export interface EoqResult {
  eoq: number;
  ordersPerYear: number;
  cycleDays: number;
  annualOrderingCost: number;
  annualHoldingCost: number;
  totalAnnualCost: number;
}

/** Economic Order Quantity: EOQ = √(2·D·S / H). */
export function eoq(input: {
  annualDemand: number;
  orderCost: number;
  holdingCostPerUnitYear: number;
}): EoqResult {
  const { annualDemand: d, orderCost: s, holdingCostPerUnitYear: h } = input;
  if (d <= 0 || s <= 0 || h <= 0) {
    return { eoq: 0, ordersPerYear: 0, cycleDays: 0, annualOrderingCost: 0, annualHoldingCost: 0, totalAnnualCost: 0 };
  }
  const q = Math.sqrt((2 * d * s) / h);
  const ordersPerYear = d / q;
  return {
    eoq: q,
    ordersPerYear,
    cycleDays: 365 / ordersPerYear,
    annualOrderingCost: ordersPerYear * s,
    annualHoldingCost: (q / 2) * h,
    totalAnnualCost: ordersPerYear * s + (q / 2) * h,
  };
}

export interface DemurrageResult {
  chargeableDays: number;
  totalCharge: number;
  perContainer: number;
}

/**
 * Demurrage/detention: chargeable = max(0, daysHeld − freeDays), billed per
 * container per day. (Same shape works for either charge — run it twice.)
 */
export function demurrage(input: {
  containers: number;
  freeDays: number;
  daysHeld: number;
  perDiemRate: number;
}): DemurrageResult {
  const { containers, freeDays, daysHeld, perDiemRate } = input;
  const chargeableDays = Math.max(0, daysHeld - freeDays);
  const perContainer = chargeableDays * perDiemRate;
  return {
    chargeableDays,
    perContainer,
    totalCharge: perContainer * Math.max(0, containers),
  };
}

export interface TurnsResult {
  turns: number;
  daysOfSupply: number;
}

/** Inventory turnover = COGS / average inventory; days of supply = 365 / turns. */
export function inventoryTurns(input: { annualCogs: number; avgInventoryValue: number }): TurnsResult {
  const { annualCogs, avgInventoryValue } = input;
  if (avgInventoryValue <= 0) return { turns: 0, daysOfSupply: 0 };
  const turns = annualCogs / avgInventoryValue;
  return { turns, daysOfSupply: turns > 0 ? 365 / turns : 0 };
}

/** Compact number formatting for results (thousands separators, capped decimals). */
export function fmt(n: number, decimals = 0): string {
  if (!isFinite(n)) return "—";
  return n.toLocaleString(undefined, { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}
