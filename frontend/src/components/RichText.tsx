import { Fragment, type ReactNode } from "react";
import { Link } from "react-router-dom";

// Matches @username tokens (usernames are [a-z0-9_], 3–30 chars) — must mirror
// the backend mention pattern in InAppNotifier.java.
const MENTION = /@([A-Za-z0-9_]{3,30})/g;

/**
 * Renders post/answer text with @mentions turned into profile links. Newlines are
 * preserved by the parent's `whitespace-pre-wrap`, so wrap this in such a element.
 */
export default function RichText({ text }: { text: string | null | undefined }) {
  if (!text) return null;

  const nodes: ReactNode[] = [];
  let last = 0;
  let match: RegExpExecArray | null;
  MENTION.lastIndex = 0;
  while ((match = MENTION.exec(text)) !== null) {
    if (match.index > last) nodes.push(<Fragment key={last}>{text.slice(last, match.index)}</Fragment>);
    const username = match[1];
    nodes.push(
      <Link
        key={match.index}
        to={`/users/${username}`}
        className="font-medium text-accent hover:underline"
        onClick={(e) => e.stopPropagation()}
      >
        @{username}
      </Link>
    );
    last = match.index + match[0].length;
  }
  if (last < text.length) nodes.push(<Fragment key={last}>{text.slice(last)}</Fragment>);

  return <>{nodes}</>;
}
