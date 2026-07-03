export interface User {
  id: string;
  username: string;
  email?: string;
  name?: string | null;
  avatarUrl?: string | null;
  role?: "USER" | "ADMIN";
  isBanned?: boolean;
  createdAt: string;
}

export interface Profile {
  user: User;
  stats: { questions: number; comments: number; upvotesReceived: number };
  questions: Question[];
  commented: Question[];
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
}

export interface Question {
  id: string;
  title: string;
  body: string;
  imageUrl: string | null;
  shareCount: number;
  voteCount: number;
  viewerHasVoted: boolean;
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
