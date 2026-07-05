export interface User {
  id: string;
  username: string;
  email?: string;
  name?: string | null;
  avatarUrl?: string | null;
  role?: "USER" | "ADMIN";
  isBanned?: boolean;
  memberType?: string | null;
  phone?: string | null;
  organization?: string | null;
  headline?: string | null;
  bio?: string | null;
  linkedinUrl?: string | null;
  verifyStatus?: "PENDING" | "APPROVED" | "REJECTED";
  topics?: string | null;
  openToMentor?: boolean;
  seekingMentor?: boolean;
  notifyAllQuestions?: boolean;
  plan?: "FREE" | "PRO";
  pro?: boolean;
  planExpiresAt?: string | null;
  reputation?: number;
  createdAt: string;
}

export interface MarketplaceLead {
  buyer: User;
  listingId: string;
  listingTitle: string;
  createdAt: string;
}

export interface Listing {
  id: string;
  kind: "OFFER" | "SEEK";
  category: string;
  title: string;
  description: string;
  location: string | null;
  price: string | null;
  size: string | null;
  imageUrl: string | null;
  createdAt: string;
  authorId: string;
  author: User;
}

export interface EventItem {
  id: string;
  title: string;
  description: string;
  link: string | null;
  startsAt: string;
  createdAt: string;
  host: User;
  rsvpCount: number;
  viewerRsvped: boolean;
}

export interface Profile {
  user: User;
  stats: { questions: number; comments: number; upvotesReceived: number; accepted: number };
  questions: Question[];
  commented: Question[];
}

export interface Leader {
  user: User;
  stats: { questions: number; comments: number; upvotesReceived: number; accepted: number };
  reputation: number;
}

export interface ChatMessage {
  id: string;
  body: string;
  createdAt: string;
  fromMe: boolean;
}

export interface Conversation {
  partner: User;
  lastMessage: { body: string; createdAt: string; fromMe: boolean };
  unread: number;
}

export interface FlaggedUser {
  id: string;
  username: string;
  name: string | null;
  avatarUrl: string | null;
  flagCount: number;
  isBanned: boolean;
  createdAt: string;
  moderationEvents: { kind: string; content: string; createdAt: string }[];
}

export interface Comment {
  id: string;
  body: string;
  imageUrl: string | null;
  createdAt: string;
  author: User;
  parentId?: string | null;
  voteCount?: number;
  viewerHasVoted?: boolean;
  comments?: Comment[];
}

export interface Question {
  id: string;
  title: string;
  body: string;
  imageUrl: string | null;
  shareCount: number;
  voteCount: number;
  viewerHasVoted: boolean;
  tag: string;
  acceptedCommentId: string | null;
  createdAt: string;
  author: User;
  comments?: Comment[];
  _count: { comments: number; votes: number };
}

export interface QuestionList {
  questions: Question[];
  total: number;
  page: number;
  pageSize: number;
}
