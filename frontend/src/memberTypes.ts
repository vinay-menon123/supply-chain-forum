export type MemberType =
  | "ACADEMICIAN"
  | "PROFESSIONAL"
  | "RESEARCHER"
  | "STUDENT"
  | "INDUSTRY_PARTNER"
  | "STARTUP_TECH_PARTNER";

export interface MemberTypeMeta {
  value: MemberType;
  emoji: string;
  label: string;
  description: string;
  badgeClass: string;
}

// The six ecosystem roles from the CSCEN business model
export const MEMBER_TYPES: MemberTypeMeta[] = [
  {
    value: "ACADEMICIAN",
    emoji: "🎓",
    label: "Academician",
    description: "Share knowledge, collaborate on research and shape future talent.",
    badgeClass: "bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300",
  },
  {
    value: "PROFESSIONAL",
    emoji: "💼",
    label: "Professional",
    description: "Learn, network, exchange ideas and advance your career.",
    badgeClass: "bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300",
  },
  {
    value: "RESEARCHER",
    emoji: "🔍",
    label: "Researcher",
    description: "Contribute insights, conduct studies and publish valuable research.",
    badgeClass: "bg-purple-100 text-purple-700 dark:bg-purple-950 dark:text-purple-300",
  },
  {
    value: "STUDENT",
    emoji: "📚",
    label: "Student",
    description: "Access learning resources, mentorship and real-world exposure.",
    badgeClass: "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
  },
  {
    value: "INDUSTRY_PARTNER",
    emoji: "🏭",
    label: "Industry Partner",
    description: "Collaborate on projects, solve challenges and co-create solutions.",
    badgeClass: "bg-cyan-100 text-cyan-700 dark:bg-cyan-950 dark:text-cyan-300",
  },
  {
    value: "STARTUP_TECH_PARTNER",
    emoji: "🚀",
    label: "Startup & Tech Partner",
    description: "Innovate, integrate and scale solutions for the ecosystem.",
    badgeClass: "bg-rose-100 text-rose-700 dark:bg-rose-950 dark:text-rose-300",
  },
];

export function memberTypeMeta(value: string | null | undefined): MemberTypeMeta | undefined {
  return MEMBER_TYPES.find((t) => t.value === value);
}
