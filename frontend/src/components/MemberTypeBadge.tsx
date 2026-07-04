import { memberTypeMeta } from "../memberTypes";

interface Props {
  memberType?: string | null;
  small?: boolean;
}

export default function MemberTypeBadge({ memberType, small = false }: Props) {
  const meta = memberTypeMeta(memberType);
  if (!meta) return null;
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full font-medium ${meta.badgeClass} ${
        small ? "px-1.5 py-0.5 text-[10px]" : "px-2.5 py-0.5 text-xs"
      }`}
    >
      {meta.emoji} {meta.label}
    </span>
  );
}
