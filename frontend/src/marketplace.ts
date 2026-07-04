export interface CategoryMeta {
  value: string;
  emoji: string;
  label: string;
}

// Marketplace categories for listings
export const CATEGORIES: CategoryMeta[] = [
  { value: "WAREHOUSE", emoji: "🏬", label: "Warehouse Space" },
  { value: "TRANSPORT", emoji: "🚛", label: "Transport & Freight" },
  { value: "EQUIPMENT", emoji: "🏗️", label: "Equipment" },
  { value: "SERVICES", emoji: "🧰", label: "Services" },
  { value: "GENERAL", emoji: "📦", label: "General Logistics" },
];

export function categoryMeta(value: string | null | undefined): CategoryMeta {
  return CATEGORIES.find((c) => c.value === value) ?? CATEGORIES[CATEGORIES.length - 1];
}

export interface KindMeta {
  value: "OFFER" | "SEEK";
  label: string;
  verb: string;
  badgeClass: string;
}

export const KINDS: KindMeta[] = [
  {
    value: "OFFER",
    label: "Offering",
    verb: "I'm offering",
    badgeClass: "bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300",
  },
  {
    value: "SEEK",
    label: "Looking for",
    verb: "I'm looking for",
    badgeClass: "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
  },
];

export function kindMeta(value: string | null | undefined): KindMeta {
  return KINDS.find((k) => k.value === value) ?? KINDS[0];
}
