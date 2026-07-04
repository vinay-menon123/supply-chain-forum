export interface TagMeta {
  value: string;
  emoji: string;
  label: string;
}

// Supply chain domain topics for questions
export const TAGS: TagMeta[] = [
  { value: "GENERAL", emoji: "💬", label: "General" },
  { value: "DEMAND_PLANNING", emoji: "📈", label: "Demand Planning" },
  { value: "PROCUREMENT", emoji: "🤝", label: "Procurement" },
  { value: "MANUFACTURING", emoji: "🏭", label: "Manufacturing" },
  { value: "LOGISTICS", emoji: "🚚", label: "Logistics" },
  { value: "WAREHOUSING", emoji: "📦", label: "Warehousing" },
  { value: "INVENTORY", emoji: "🗃️", label: "Inventory" },
  { value: "SUSTAINABILITY", emoji: "🌱", label: "Sustainability" },
  { value: "DIGITAL_AI", emoji: "🤖", label: "Digital & AI" },
  { value: "RISK", emoji: "⚠️", label: "Risk Management" },
  { value: "CAREERS", emoji: "🧭", label: "Careers" },
];

export function tagMeta(value: string | null | undefined): TagMeta {
  return TAGS.find((t) => t.value === value) ?? TAGS[0];
}
