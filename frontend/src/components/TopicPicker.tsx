import { TAGS } from "../tags";

/** Multi-select chips for the domain topics a member follows. */
export default function TopicPicker({
  selected,
  onToggle,
}: {
  selected: string[];
  onToggle: (value: string) => void;
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {TAGS.filter((t) => t.value !== "GENERAL").map((t) => {
        const on = selected.includes(t.value);
        return (
          <button
            type="button"
            key={t.value}
            onClick={() => onToggle(t.value)}
            className={`rounded-full border px-3 py-1.5 text-sm font-medium transition ${
              on
                ? "border-transparent bg-gradient-to-r from-indigo-600 to-violet-600 text-white shadow-sm shadow-indigo-500/30"
                : "border-slate-300 text-slate-600 hover:border-indigo-300 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
            }`}
          >
            {t.emoji} {t.label}
            {on && <span className="ml-1">✓</span>}
          </button>
        );
      })}
    </div>
  );
}

/** Parse/format the comma-separated topics string stored on the user. */
export function parseTopics(raw: string | null | undefined): string[] {
  if (!raw) return [];
  return raw
    .split(",")
    .map((t) => t.trim().toUpperCase())
    .filter(Boolean);
}

export function formatTopics(list: string[]): string {
  return list.join(",");
}
