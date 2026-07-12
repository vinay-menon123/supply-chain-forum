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

export interface Notification {
  id: string;
  type: "ANSWER" | "REPLY" | "ACCEPT" | "MENTION";
  text: string;
  questionId: string | null;
  commentId: string | null;
  read: boolean;
  createdAt: string;
  actor: User | null;
}

export interface Job {
  id: string;
  title: string;
  company: string;
  location: string | null;
  employmentType: "FULL_TIME" | "PART_TIME" | "CONTRACT" | "INTERNSHIP";
  tag: string;
  description: string;
  applyUrl: string | null;
  salary: string | null;
  createdAt: string;
  authorId: string;
  author: User;
}

export interface Template {
  id: string;
  title: string;
  description: string;
  category: string;
  fileUrl: string;
  fileName: string;
  fileType: string | null;
  downloadCount: number;
  voteCount: number;
  viewerHasVoted: boolean;
  createdAt: string;
  authorId: string;
  author: User;
}

// ── AI Agent control tower ──
export type FactorSource = "LIVE" | "MODELED" | "COMPUTED" | "ROADMAP";
export type FactorImpact = "INFO" | "GOOD" | "CAUTION" | "CRITICAL";

export interface ErpVehicle {
  id: string;
  plate: string;
  type: string;
  capacityTons: number;
  currentCity: string;
  status: "BREAKDOWN" | "IDLE" | "EN_ROUTE";
  driver: string;
  driverHosLeftHrs?: number;
  fuelPct?: number;
  ageYears?: number;
  failureRatePct?: number;
  reefer?: boolean;
  note: string;
}
export interface ErpCarrier {
  id: string;
  name: string;
  costPerKm: number;
  reliabilityPct: number;
  avgResponseHrs: number;
  claimsRatePct?: number;
  spot?: boolean;
  compliant?: boolean;
  lanes: string[];
  note: string;
}
export interface ErpAdvisory {
  area: string;
  route: string;
  type: "WEATHER" | "FESTIVAL" | "BLOCKAGE";
  severity: "LOW" | "MODERATE" | "HIGH";
  delayHrs: number;
  message: string;
}
export interface ErpShipment {
  id: string;
  ref: string;
  cargo: string;
  tons: number;
  origin: string;
  destination: string;
  route: string;
  remainingKm: number;
  slaHoursRemaining: number;
  value: number;
  priority: "CRITICAL" | "HIGH" | "MEDIUM" | string;
  vehicleId: string;
  breakdownIssue: string | null;
  customerTier?: string;
  penaltyPerHourInr?: number;
  perishable?: boolean;
  tempControlled?: boolean;
}
export interface ErpSnapshot {
  company: string;
  vehicles: ErpVehicle[];
  carriers: ErpCarrier[];
  depots: { city: string; note: string }[];
  advisories: ErpAdvisory[];
  shipments: ErpShipment[];
}

export interface AgentFactor {
  code: string;
  label: string;
  value: string;
  source: FactorSource;
  impact: FactorImpact;
}
export interface AgentReport {
  name: string;
  role: string;
  emoji: string;
  domain: string;
  headline: string;
  factors: AgentFactor[];
}
export interface CostLine {
  label: string;
  amount: number;
}

// ── distribution / demand side (multi-drop SKU x channel consignments) ──
export interface ChannelFill {
  channel: string;
  channelName: string;
  demand: number;
  onTimeUnits: number;
  lateUnits: number;
  unfilledUnits: number;
  fillPct: number;
  etaHrs: number;
  promiseHrs: number;
  penaltyInr: number;
  verdict: string;
}
export interface CityPlan {
  city: string;
  demand: number;
  onTimeUnits: number;
  fillPct: number;
  etaHrs: number;
  channels: ChannelFill[];
}
export interface SkuPlan {
  sku: string;
  name: string;
  demand: number;
  fromRdc: number;
  fromTruck: number;
  unfilled: number;
  fillPct: number;
}
export interface SourceLine {
  source: string;
  kind: "RDC" | "TRUCK" | string;
  city: string;
  units: number;
  etaHrs: number;
  freightInr: number;
}
export interface Backfill {
  rdc: string;
  unitsLent: number;
  unitsRepaid: number;
  repaidInHrs: number;
  note: string;
}
export interface FulfilmentPlan {
  strategyId: string;
  totalDemand: number;
  onTimeUnits: number;
  lateUnits: number;
  unfilledUnits: number;
  fillPct: number;
  freightInr: number;
  penaltyInr: number;
  handlingInr: number;
  totalCostInr: number;
  cities: CityPlan[];
  channelTotals: ChannelFill[];
  skus: SkuPlan[];
  sources: SourceLine[];
  backfills: Backfill[];
  summary: string;
}
export interface Stakeholder {
  department: string;
  scope: string;
  action: string;
  urgency: "IMMEDIATE" | "NEXT_CYCLE" | "FYI" | string;
}

export interface AgentOption {
  id: string;
  title: string;
  type:
    | "EXTERNAL_CARRIER" | "OWN_FLEET" | "REPAIR" | "ROUTE_CHANGE" | "MODE_RAIL" | "MODE_AIR" | "ALT_DC"
    | "REPLACEMENT_TRANSPORTER" | "RDC_NEAR" | "RDC_POOL" | "HYBRID" | string;
  provider: string;
  etaHours: number;
  onTime: boolean;
  onTimeProbPct: number;
  reliabilityPct: number;
  expectedCost: number;
  costBreakdown: CostLine[];
  co2Kg: number;
  score: number;
  recommended: boolean;
  summary: string | null;
  pros: string[];
  cons: string[];
  risks: string[];
  plan: FulfilmentPlan | null;
}
export interface SignalView {
  kind: "WEATHER" | "FESTIVAL" | "EWAYBILL" | "BLOCKAGE" | string;
  label: string;
  detail: string;
  source: FactorSource;
  impact: FactorImpact;
}
export interface AgentScenario {
  shipmentId: string;
  ref: string;
  cargo: string;
  tons: number;
  route: string;
  origin: string;
  destination: string;
  priority: string;
  value: number;
  remainingKm: number;
  slaHoursRemaining: number;
  vehiclePlate: string;
  disruption: string;
  customerTier: string;
  summary: string;
}
export interface Evidence {
  label: string;
  value: string;
  source: FactorSource;
}
export interface AgentRun {
  scenario: AgentScenario;
  signals: SignalView[];
  agents: AgentReport[];
  options: AgentOption[];
  recommendation: { optionId: string; title: string; rationale: string; evidence: Evidence[] };
  stakeholders: Stakeholder[];
  factorsConsidered: number;
  aiPowered: boolean;
  aiProvider: string;
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
