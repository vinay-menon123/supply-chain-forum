export interface User {
  id: string;
  username: string;
  email?: string;
  name?: string | null;
  avatarUrl?: string | null;
  createdAt: string;
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
