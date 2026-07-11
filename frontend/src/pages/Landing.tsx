import { FormEvent, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api";
import { MEMBER_TYPES } from "../memberTypes";
import { TAGS } from "../tags";
import type { Leader, Question, QuestionList } from "../types";

const TOGETHER = [
  { emoji: "🔗", title: "Connect", text: "Build meaningful connections across the supply chain ecosystem." },
  { emoji: "🤝", title: "Collaborate", text: "Work together to solve real-world logistical challenges." },
  { emoji: "💡", title: "Contribute", text: "Share knowledge, operational insights, and best practices." },
  { emoji: "🚀", title: "Innovate", text: "Drive innovation through technologies and collective intelligence." },
  { emoji: "⭐", title: "Lead", text: "Develop leaders and shape the global future of logistics." },
];

const FEATURES = [
  {
    emoji: "💬",
    title: "Ask & Answer",
    text: "Get practical, verified answers from practitioners on demand planning, procurement, warehousing, and transportation.",
    to: "/questions",
  },
  {
    emoji: "💼",
    title: "Careers & Jobs",
    text: "Browse supply-chain roles across planning, procurement, warehousing, and logistics — or post openings to a network of verified practitioners.",
    to: "/jobs",
  },
  {
    emoji: "🤝",
    title: "Find a Mentor",
    text: "Match with experienced directors — or give back by mentoring the next generation of supply chain managers.",
    to: "/mentorship",
  },
  {
    emoji: "📅",
    title: "Events & Webinars",
    text: "Join exclusive panels and webinars hosted by industry experts, RSVP in one tap, and never miss what matters.",
    to: "/events",
  },
];

const ROLE_DETAILS: Record<string, { title: string; subtitle: string; emoji: string; details: string }> = {
  ACADEMICIAN: {
    title: "Academician",
    subtitle: "Talent Development & Advisory",
    emoji: "🎓",
    details: "Academicians represent professors, lecturers, and educational leaders in the supply chain field. They bridge the gap between theoretical research and operations by leading webinars, advising on workforce certifications, and shaping the future talent pool."
  },
  PROFESSIONAL: {
    title: "Professional",
    subtitle: "Operational Execution & Peer Exchange",
    emoji: "💼",
    details: "Professionals are active managers, directors, and executives. They use the platform to resolve day-to-day operations roadblocks, benchmark carrier performance, and consult peers on supply network design and optimization strategies."
  },
  RESEARCHER: {
    title: "Researcher",
    subtitle: "Insights, Trend Studies & Publishing",
    emoji: "🔍",
    details: "Researchers study long-term industry shifts, sustainability frameworks, and supply chain tech disruptions. They share whitepapers, publish studies, and provide data-driven insights to keep the ecosystem ahead of global trends."
  },
  STUDENT: {
    title: "Student",
    subtitle: "Learning Resources & Career Mentorship",
    emoji: "📚",
    details: "Students are the next generation of logistics coordinators. They access corporate case studies, read real-world operations realities, and connect with verified mentors to bridge the transition into professional employment."
  },
  INDUSTRY_PARTNER: {
    title: "Industry Partner",
    subtitle: "Capacity Coordination & Hiring",
    emoji: "🏭",
    details: "Industry Partners represent logistics providers, warehouse operators, and fleet carriers. They post openings on the jobs board, share operational playbooks and templates, and connect with talent directly within the verified network."
  },
  STARTUP_TECH_PARTNER: {
    title: "Startup & Tech Partner",
    subtitle: "Digital Integration & Transformation",
    emoji: "🚀",
    details: "Startup and Tech Partners showcase innovations in blockchain ledger tracking, AI demand planning models, and warehouse robotics. They consult members on digital transformation and integrate API tools."
  }
};

function useCountUp(target: number, durationMs = 1400): number {
  const [value, setValue] = useState(0);
  // Track the currently displayed value so a new target (e.g. the 5-min poll
  // bumping the question count) animates smoothly from where we are now.
  const fromRef = useRef(0);

  useEffect(() => {
    const from = fromRef.current;
    if (from === target) return;
    const start = performance.now();
    let frame: number;
    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / durationMs);
      const eased = 1 - Math.pow(1 - progress, 3);
      const current = Math.round(from + (target - from) * eased);
      setValue(current);
      fromRef.current = current;
      if (progress < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [target, durationMs]);

  return value;
}

function Stat({ value, label }: { value: number; label: string }) {
  const display = useCountUp(value);
  return (
    <div className="flex flex-col">
      <span className="text-3xl font-semibold tracking-tight text-white sm:text-4xl">
        {display}
      </span>
      <span className="mt-1 text-xs text-[#8A8F98] uppercase tracking-wider font-mono">
        {label}
      </span>
    </div>
  );
}

function BentoCard({ children, className = "", spanClass = "" }: { children: React.ReactNode; className?: string; spanClass?: string }) {
  const cardRef = useRef<HTMLDivElement>(null);

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!cardRef.current) return;
    const rect = cardRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    cardRef.current.style.setProperty("--mouse-x", `${x}px`);
    cardRef.current.style.setProperty("--mouse-y", `${y}px`);
  };

  return (
    <div 
      ref={cardRef}
      onMouseMove={handleMouseMove}
      className={`card card-hover spotlight-card w-full min-w-0 overflow-hidden ${className} ${spanClass}`}
    >
      <div className="relative z-10 h-full flex flex-col justify-between w-full min-w-0 overflow-hidden">
        {children}
      </div>
    </div>
  );
}

function InteractiveDemo({ realQuestions }: { realQuestions: Question[] }) {
  const navigate = useNavigate();
  const [typedTitle, setTypedTitle] = useState("");
  const [selectedTag, setSelectedTag] = useState("LOGISTICS");
  const [step, setStep] = useState(0); // 0: Idle, 1: Typing, 2: Checking, 3: Posted
  const timerRef = useRef<number | null>(null);

  const DEMO_QUESTIONS = realQuestions && realQuestions.length > 0
    ? realQuestions.slice(0, 3).map((q) => ({
        id: q.id,
        title: q.title,
        tag: q.tag,
        author: q.author.name || q.author.username,
        avatar: q.author.username.substring(0, 2).toUpperCase(),
        body: q.body
      }))
    : [
        {
          id: "",
          title: "Optimizing warehouse space for seasonal cold chain products",
          tag: "WAREHOUSING",
          author: "Priya Sharma",
          avatar: "PS",
          body: "We are facing capacity constraints for refrigerated storage during the summer surge. What routing or zoning strategies have you implemented to maximize slotting efficiency?"
        },
        {
          id: "",
          title: "Mitigating ocean freight container delays in Asia-Europe lanes",
          tag: "TRANSPORT",
          author: "Daniel Okafor",
          avatar: "DO",
          body: "With ongoing disruptions, we are evaluating alternative multimodal routings (rail-sea combinations). Has anyone run cost-benefit analyses on these transshipment options?"
        },
        {
          id: "",
          title: "How to draft robust SLAs for third-party logistics (3PL) partners?",
          tag: "PROCUREMENT",
          author: "Meera Iyer",
          avatar: "MI",
          body: "We need to structure key performance indicators (KPIs) around on-time delivery and inventory accuracy. What penalties or incentive models are standard in cold-storage contracts?"
        }
      ];

  const handleSelectDemo = (index: number) => {
    if (timerRef.current) clearInterval(timerRef.current);
    const q = DEMO_QUESTIONS[index];
    if (!q) return;
    setStep(1);
    setTypedTitle("");
    setSelectedTag(q.tag);
    
    let currentText = "";
    let charIndex = 0;
    
    timerRef.current = setInterval(() => {
      if (charIndex < q.title.length) {
        currentText += q.title[charIndex];
        setTypedTitle(currentText);
        charIndex++;
      } else {
        if (timerRef.current) clearInterval(timerRef.current);
        setStep(2);
        
        // Wait 1s then mark as Posted
        setTimeout(() => {
          setStep(3);
        }, 1000);
      }
    }, 20) as unknown as number;
  };

  useEffect(() => {
    const t = setTimeout(() => {
      if (DEMO_QUESTIONS.length > 0) handleSelectDemo(0);
    }, 600);
    return () => {
      clearTimeout(t);
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [realQuestions]);

  const activeQuestion = DEMO_QUESTIONS.find(q => q.title === typedTitle);
  const qId = activeQuestion?.id;

  const navigateToQuestion = () => {
    if (qId) {
      navigate(`/questions/${qId}`);
    } else {
      navigate("/questions");
    }
  };

  return (
    <div className="p-6 md:p-8 flex flex-col md:flex-row lg:flex-col xl:flex-row gap-6 items-stretch w-full overflow-hidden">
      {/* Simulation Controls */}
      <div className="flex-1 min-w-0 flex flex-col justify-between">
        <div>
          <span className="text-[10px] font-semibold text-accent uppercase tracking-widest font-mono">
            Interactive Portal Feed
          </span>
          <h3 className="text-lg font-semibold tracking-tight text-white mt-1 mb-2">
            AI Gated Q&A Verification
          </h3>
          <p className="text-xs text-[#8A8F98] leading-relaxed mb-5">
            Select an operational query below to verify LinkedIn credentials, classify topic taxonomy, and broadcast to the network.
          </p>
          
          <div className="space-y-2">
            {DEMO_QUESTIONS.map((q, idx) => (
              <button
                key={idx}
                onClick={() => handleSelectDemo(idx)}
                className="w-full text-left p-2.5 rounded-lg border border-white/[0.06] bg-white/[0.02] hover:border-accent/40 hover:bg-white/[0.04] transition-all duration-200 flex items-center gap-3 group"
              >
                <span className="text-xs font-semibold text-[#8A8F98] group-hover:text-accent font-mono">
                  0{idx + 1}
                </span>
                <div className="min-w-0">
                  <p className="text-xs font-medium text-white truncate group-hover:text-accent transition-colors">
                    {q.title}
                  </p>
                  <p className="text-[10px] text-[#8A8F98] mt-0.5 font-mono uppercase tracking-wider">
                    {q.tag}
                  </p>
                </div>
              </button>
            ))}
          </div>
        </div>
        
        <div className="mt-4 pt-4 border-t border-white/[0.06]">
          <span className="text-[9px] text-[#8A8F98]/75 tracking-wider uppercase font-mono block">
            Verification status: <span className="text-[#5E6AD2] font-semibold">Active & Live</span>
          </span>
        </div>
      </div>

      {/* Vertical divider */}
      <div className="hidden md:block lg:hidden xl:block w-px bg-white/[0.06] self-stretch" />

      {/* Live Rendering Panel */}
      <div className="flex-1 min-w-0 bg-[#020203] border border-white/[0.04] rounded-lg p-4 relative min-h-[220px] flex flex-col justify-between">
        {/* Terminal Header */}
        <div className="absolute top-3 right-3 flex gap-1.5 items-center">
          <div className="w-1.5 h-1.5 rounded-full bg-white/10"></div>
          <div className="w-1.5 h-1.5 rounded-full bg-white/20"></div>
          <div className="w-1.5 h-1.5 rounded-full bg-accent animate-pulse"></div>
        </div>
        
        {step === 1 && (
          <div className="flex-1 flex flex-col justify-center items-center py-6">
            <span className="text-[10px] text-accent animate-pulse uppercase tracking-wider font-mono">
              typing question...
            </span>
            <p className="text-sm font-medium text-white text-center mt-3 max-w-xs italic leading-relaxed">
              "{typedTitle}"
            </p>
          </div>
        )}
        
        {step === 2 && (
          <div className="flex-1 flex flex-col justify-center items-center py-6">
            <div className="w-6 h-6 border-2 border-t-accent border-white/10 rounded-full animate-spin"></div>
            <span className="text-[10px] text-accent uppercase tracking-wider mt-4 font-mono">
              analyzing relevance...
            </span>
            <p className="text-[9px] text-[#8A8F98] mt-1 font-sans">
              Matching LinkedIn credentials and supply chain taxonomy
            </p>
          </div>
        )}

        {step === 3 && (
          <div 
            onClick={navigateToQuestion}
            className="flex-1 flex flex-col justify-between animate-fade-in cursor-pointer group/terminal"
          >
            <div>
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-3 border-b border-white/[0.06] pb-2">
                <div className="flex items-center gap-2">
                  <div className="w-6 h-6 rounded-full bg-white/5 border border-white/10 flex items-center justify-center text-[10px] font-bold text-white flex-shrink-0">
                    {activeQuestion?.avatar || "PS"}
                  </div>
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="text-xs font-semibold text-white truncate max-w-[100px] sm:max-w-none">
                        {activeQuestion?.author || "Priya Sharma"}
                      </span>
                      <span className="text-[8px] bg-accent/15 text-accent border border-accent/30 font-bold px-1 py-0.2 rounded flex-shrink-0">
                        ✓ VERIFIED
                      </span>
                    </div>
                  </div>
                </div>
                <span className="text-[9px] text-accent bg-accent/5 border border-accent/20 px-1.5 py-0.5 rounded font-mono self-start sm:self-auto flex-shrink-0">
                  {selectedTag}
                </span>
              </div>
              
              <h4 className="text-xs font-semibold text-white leading-normal tracking-wide group-hover/terminal:text-accent transition-colors">
                {typedTitle}
              </h4>
              
              <p className="text-[10px] text-[#8A8F98] mt-2 leading-relaxed line-clamp-3 font-sans">
                {activeQuestion?.body || ""}
              </p>
            </div>
            
            <div className="border-t border-white/[0.06] pt-2.5 mt-3 flex items-center justify-between text-[9px] font-mono text-[#8A8F98]">
              <div className="flex items-center gap-3">
                <span>▲ 16 upvotes</span>
                <span>💬 2 comments</span>
              </div>
              <span className="text-accent group-hover/terminal:underline font-semibold">
                View thread →
              </span>
            </div>
          </div>
        )}
        
        {step === 0 && (
          <div className="flex-1 flex flex-col justify-center items-center py-6 text-center">
            <span className="text-[11px] text-[#8A8F98] max-w-[200px] leading-relaxed">
              Click an operations query on the left to trigger the verification pipeline
            </span>
          </div>
        )}
      </div>
    </div>
  );
}

export default function Landing() {
  const [questionCount, setQuestionCount] = useState(0);
  const [contributorCount, setContributorCount] = useState(0);
  const [realQuestions, setRealQuestions] = useState<Question[]>([]);
  const [selectedRole, setSelectedRole] = useState<string | null>(null);

  // Scroll position for parallax
  const [scrollY, setScrollY] = useState(0);

  // RSVPs for Interactive widgets
  const [rsvps, setRsvps] = useState<string[]>([]);

  // Contact form state
  const [contactName, setContactName] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [contactMessage, setContactMessage] = useState("");
  const [submittingContact, setSubmittingContact] = useState(false);
  const [contactSuccess, setContactSuccess] = useState(false);
  const [contactError, setContactError] = useState("");



  useEffect(() => {
    const handleScroll = () => setScrollY(window.scrollY);
    window.addEventListener("scroll", handleScroll, { passive: true });

    // Refresh the live community metrics; called on mount and every 5 minutes.
    // We only seed the demo questions on the first load and then keep the
    // reference stable, so polling never restarts the typing simulation.
    const loadMetrics = () => {
      api<QuestionList>("/questions?pageSize=4").then((d) => {
        setQuestionCount(d.total);
        setRealQuestions((prev) => (prev.length === 0 ? d.questions : prev));
      }).catch(() => {});

      api<{ leaders: Leader[] }>("/leaderboard")
        .then((d) => setContributorCount(d.leaders.length))
        .catch(() => {});
    };

    loadMetrics();
    const metricsTimer = window.setInterval(loadMetrics, 5 * 60 * 1000);

    return () => {
      window.removeEventListener("scroll", handleScroll);
      window.clearInterval(metricsTimer);
    };
  }, []);

  function handleRsvp(id: string) {
    setRsvps((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  }

  async function handleContactSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmittingContact(true);
    setContactError("");
    try {
      await api("/contact", {
        method: "POST",
        body: JSON.stringify({
          name: contactName,
          email: contactEmail,
          message: contactMessage,
        }),
      });
      setContactSuccess(true);
      setContactName("");
      setContactEmail("");
      setContactMessage("");
    } catch (err) {
      setContactError(err instanceof Error ? err.message : "Failed to submit request");
    } finally {
      setSubmittingContact(false);
    }
  }

  const heroOpacity = Math.max(0, 1 - scrollY / 450);
  const heroScale = Math.max(0.96, 1 - scrollY / 6000);
  const heroTranslateY = -scrollY * 0.15;

  return (
    <div className="relative overflow-hidden min-h-screen py-16 px-6 sm:px-8 selection:bg-accent/30 selection:text-white">
      
      {/* Hero Section */}
      <section 
        style={{ 
          opacity: heroOpacity, 
          transform: `scale(${heroScale}) translateY(${heroTranslateY}px)` 
        }}
        className="relative mx-auto max-w-4xl text-center pt-16 pb-20 sm:pt-24 sm:pb-32"
      >
        <span className="inline-flex items-center gap-2 rounded-full border border-white/[0.08] bg-white/[0.03] px-3.5 py-1 text-xs font-medium text-accent backdrop-blur hover:border-white/[0.12] transition-colors duration-200 select-none">
          ✨ Centre for Supply Chain Excellence
        </span>
        
        <h1 className="mt-8 text-5xl sm:text-7xl lg:text-8xl font-bold tracking-[-0.03em] leading-none text-white">
          Where supply chain
          <br />
          <span className="text-transparent bg-clip-text bg-gradient-to-r from-accent via-indigo-300 to-accent bg-[length:200%_auto] animate-gradient-x">
            minds collaborate.
          </span>
        </h1>
        
        <p className="mx-auto mt-6 max-w-2xl text-base sm:text-lg text-[#8A8F98] leading-relaxed font-normal tracking-wide">
          CSCE Nexus is a verified Q&A, mentorship, and logistics community for supply chain executives. 
          Connect directly with professionals, list resources, and build reputation among peers.
        </p>
        
        <div className="mt-10 flex flex-col sm:flex-row items-center justify-center gap-4">
          <Link to="/login" className="btn-primary w-full sm:w-auto px-6 py-3">
            Join the Community
          </Link>
          <Link to="/questions" className="btn-secondary w-full sm:w-auto px-6 py-3">
            Explore Feed
          </Link>
        </div>
      </section>

      {/* Asymmetric Bento Grid Section */}
      <section className="relative mx-auto max-w-5xl pb-24">
        <div className="text-center mb-10">
          <span className="text-xs font-semibold uppercase tracking-widest font-mono text-accent">
            Core Frameworks
          </span>
          <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-white mt-1">
            Built for practitioners, by practitioners
          </h2>
        </div>

        <div className="grid gap-4 lg:grid-cols-6 lg:grid-rows-2 w-full min-w-0">
          {/* Hero Bento: Interactive Simulator (spans 4 cols, 2 rows) */}
          <div className="lg:col-span-4 lg:row-span-2 w-full min-w-0">
            <BentoCard className="p-0 border-white/[0.06] bg-gradient-to-b from-white/[0.04] to-white/[0.01]">
              <InteractiveDemo realQuestions={realQuestions} />
            </BentoCard>
          </div>

          {/* Stat Bento Card (spans 2 cols) */}
          <div className="lg:col-span-2 w-full min-w-0">
            <BentoCard className="bg-gradient-to-b from-white/[0.06] to-white/[0.01] p-6 justify-between flex-col h-full min-h-[160px]">
              <div>
                <span className="text-[10px] font-mono uppercase tracking-wider text-[#8A8F98]">
                  Community Metrics
                </span>
                <p className="text-xs text-[#8A8F98] mt-1 leading-normal">
                  Real-time network engagement statistics.
                </p>
              </div>
              <div className="grid grid-cols-3 gap-2 mt-4 pt-4 border-t border-white/[0.06]">
                <Stat value={questionCount} label="Q&As" />
                <Stat value={contributorCount} label="PEERS" />
                <Stat value={TAGS.length - 1} label="SECTORS" />
              </div>
            </BentoCard>
          </div>

          {/* Quick Action Bento Card (spans 2 cols) */}
          <div className="lg:col-span-2 w-full min-w-0">
            <BentoCard className="bg-gradient-to-b from-white/[0.06] to-white/[0.01] p-6 justify-between flex-col h-full min-h-[160px]">
              <div>
                <span className="text-[10px] font-mono uppercase tracking-wider text-accent">
                  Resource Hub
                </span>
                <h4 className="text-sm font-semibold text-white mt-1.5">
                  Templates Library
                </h4>
                <p className="text-xs text-[#8A8F98] mt-1 leading-relaxed">
                  Download practitioner-built S&amp;OP decks, RFQ sheets, and planning models — or share your own.
                </p>
              </div>
              <div className="mt-4">
                <Link to="/templates" className="text-xs font-semibold text-white hover:text-accent transition-colors flex items-center gap-1">
                  Browse Templates <span>→</span>
                </Link>
              </div>
            </BentoCard>
          </div>
        </div>
      </section>

      {/* Feature Split section */}
      <section className="relative mx-auto max-w-5xl pb-24">
        <div className="w-full h-px bg-gradient-to-r from-transparent via-white/[0.06] to-transparent mb-20" />
        
        <div className="text-center mb-12">
          <span className="text-xs font-semibold uppercase tracking-widest font-mono text-accent">
            Core Modules
          </span>
          <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-white mt-1">
            Gated features for verified partners
          </h2>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          {FEATURES.map((f) => (
            <BentoCard key={f.title} className="bg-gradient-to-b from-white/[0.04] to-white/[0.01] p-6">
              <div className="flex items-start gap-4">
                <span className="grid h-10 w-10 flex-none place-items-center rounded-lg bg-white/[0.04] border border-white/[0.08] text-xl">
                  {f.emoji}
                </span>
                <div>
                  <h3 className="text-sm font-semibold text-white flex items-center gap-1.5">
                    {f.title}
                  </h3>
                  <p className="mt-1 text-xs text-[#8A8F98] leading-relaxed">
                    {f.text}
                  </p>
                  <Link to={f.to} className="inline-block mt-3 text-xs font-semibold text-accent hover:text-white transition-colors">
                    Access module <span>→</span>
                  </Link>
                </div>
              </div>
            </BentoCard>
          ))}
        </div>
      </section>

      {/* Podcasts and Events Section */}
      <section className="relative mx-auto max-w-5xl pb-24">
        <div className="w-full h-px bg-gradient-to-r from-transparent via-white/[0.06] to-transparent mb-20" />
        
        <div className="text-center mb-12">
          <span className="text-xs font-semibold uppercase tracking-widest font-mono text-accent">
            Live Schedule
          </span>
          <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-white mt-1">
            Incoming Podcasts & Events
          </h2>
        </div>

        <div className="grid gap-4 md:grid-cols-3">
          {/* Event 1 */}
          <div className="card bg-gradient-to-b from-white/[0.04] to-white/[0.01] border-white/[0.06] p-5 rounded-xl flex flex-col justify-between">
            <div>
              <span className="text-[9px] bg-accent/15 text-accent border border-accent/30 px-2 py-0.5 rounded font-bold uppercase tracking-wider font-mono">
                🎙 podcast
              </span>
              <h3 className="text-sm font-semibold text-white mt-3 leading-snug">
                Ep 42: Digitizing the Cold Chain
              </h3>
              <p className="text-xs text-[#8A8F98] mt-1">
                Featuring Priya Sharma (Logistics Advisor)
              </p>
            </div>
            <div className="mt-6 pt-3.5 border-t border-white/[0.04] flex items-center justify-between text-xs">
              <span className="text-[#8A8F98] font-mono text-[10px]">
                July 12 • 10:00 AM
              </span>
              <button
                onClick={() => handleRsvp("podcast1")}
                className={`text-[10px] font-semibold py-1 px-3 rounded transition ${
                  rsvps.includes("podcast1")
                    ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                    : "bg-white/5 border border-white/10 text-[#EDEDEF] hover:bg-white/8"
                }`}
              >
                {rsvps.includes("podcast1") ? "✓ RSVP'd" : "RSVP"}
              </button>
            </div>
          </div>

          {/* Event 2 */}
          <div className="card bg-gradient-to-b from-white/[0.04] to-white/[0.01] border-white/[0.06] p-5 rounded-xl flex flex-col justify-between">
            <div>
              <span className="text-[9px] bg-indigo-500/15 text-indigo-300 border border-indigo-500/20 px-2 py-0.5 rounded font-bold uppercase tracking-wider font-mono">
                💻 webinar
              </span>
              <h3 className="text-sm font-semibold text-white mt-3 leading-snug">
                AI & LLMs in Demand Planning
              </h3>
              <p className="text-xs text-[#8A8F98] mt-1">
                Interactive case studies with Daniel Okafor
              </p>
            </div>
            <div className="mt-6 pt-3.5 border-t border-white/[0.04] flex items-center justify-between text-xs">
              <span className="text-[#8A8F98] font-mono text-[10px]">
                July 18 • 2:00 PM
              </span>
              <button
                onClick={() => handleRsvp("webinar1")}
                className={`text-[10px] font-semibold py-1 px-3 rounded transition ${
                  rsvps.includes("webinar1")
                    ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                    : "bg-white/5 border border-white/10 text-[#EDEDEF] hover:bg-white/8"
                }`}
              >
                {rsvps.includes("webinar1") ? "✓ RSVP'd" : "RSVP"}
              </button>
            </div>
          </div>

          {/* Event 3 */}
          <div className="card bg-gradient-to-b from-white/[0.04] to-white/[0.01] border-white/[0.06] p-5 rounded-xl flex flex-col justify-between">
            <div>
              <span className="text-[9px] bg-purple-500/15 text-purple-300 border border-purple-500/20 px-2 py-0.5 rounded font-bold uppercase tracking-wider font-mono">
                🤝 panel
              </span>
              <h3 className="text-sm font-semibold text-white mt-3 leading-snug">
                Ocean Cargo Capacity Constraints
              </h3>
              <p className="text-xs text-[#8A8F98] mt-1">
                Sourcing resilience roundtable discussion
              </p>
            </div>
            <div className="mt-6 pt-3.5 border-t border-white/[0.04] flex items-center justify-between text-xs">
              <span className="text-[#8A8F98] font-mono text-[10px]">
                Aug 03 • 11:00 AM
              </span>
              <button
                onClick={() => handleRsvp("panel1")}
                className={`text-[10px] font-semibold py-1 px-3 rounded transition ${
                  rsvps.includes("panel1")
                    ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                    : "bg-white/5 border border-white/10 text-[#EDEDEF] hover:bg-white/8"
                }`}
              >
                {rsvps.includes("panel1") ? "✓ RSVP'd" : "RSVP"}
              </button>
            </div>
          </div>
        </div>
      </section>

      {/* Participation Roles Showcase */}
      <section className="relative mx-auto max-w-5xl pb-24">
        <div className="w-full h-px bg-gradient-to-r from-transparent via-white/[0.06] to-transparent mb-20" />
        
        <div className="text-center mb-12">
          <span className="text-xs font-semibold uppercase tracking-widest font-mono text-accent">
            Ecosystem Directory
          </span>
          <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-white mt-1">
            Six Roles of Engagement
          </h2>
        </div>

        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {MEMBER_TYPES.map((type) => (
            <div
              key={type.value}
              className="card bg-gradient-to-b from-white/[0.04] to-white/[0.01] border-white/[0.06] p-5 flex flex-col justify-between rounded-xl hover:border-white/[0.1] hover:shadow-[0_4px_20px_rgba(0,0,0,0.5)] transition-all duration-200"
            >
              <div>
                <div className="flex items-center justify-between mb-3.5">
                  <span className="grid h-9 w-9 place-items-center rounded-lg bg-white/[0.03] border border-white/[0.06] text-lg select-none">
                    {type.emoji}
                  </span>
                  <span className="text-[10px] font-mono text-[#8A8F98] tracking-widest uppercase">
                    Partner
                  </span>
                </div>
                <h3 className="text-sm font-semibold text-white tracking-wide">
                  {type.label}
                </h3>
                <p className="mt-1.5 text-xs text-[#8A8F98] leading-relaxed">
                  {type.description}
                </p>
              </div>
              <div className="mt-5 pt-3.5 border-t border-white/[0.04]">
                <button 
                  type="button"
                  onClick={() => setSelectedRole(type.value)}
                  className="text-[10px] text-accent hover:underline cursor-pointer font-semibold"
                >
                  Browse directory →
                </button>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Role details popup modal */}
      {selectedRole && ROLE_DETAILS[selectedRole] && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-fade-in">
          <div className="card max-w-md w-full bg-bg-elevated/95 border-white/10 p-6 rounded-2xl relative shadow-[0_8px_32px_rgba(0,0,0,0.85)] animate-scale-in">
            {/* Close Button */}
            <button 
              onClick={() => setSelectedRole(null)}
              className="absolute top-4 right-4 text-[#8A8F98] hover:text-white text-lg p-1"
              title="Close"
            >
              ✕
            </button>
            
            <div className="flex items-center gap-3.5 mb-4">
              <span className="grid h-12 w-12 place-items-center rounded-xl bg-accent/10 border border-accent/20 text-2xl">
                {ROLE_DETAILS[selectedRole].emoji}
              </span>
              <div>
                <h3 className="text-base font-semibold text-white">
                  {ROLE_DETAILS[selectedRole].title}
                </h3>
                <p className="text-xs text-[#8A8F98] font-mono uppercase tracking-wider mt-0.5">
                  {ROLE_DETAILS[selectedRole].subtitle}
                </p>
              </div>
            </div>
            
            <p className="text-xs text-[#8A8F98] leading-relaxed mb-6 font-sans">
              {ROLE_DETAILS[selectedRole].details}
            </p>
            
            <div className="flex gap-3">
              <Link 
                to="/login"
                className="btn-primary flex-1 text-center py-2 text-xs"
              >
                Join as {ROLE_DETAILS[selectedRole].title}
              </Link>
              <button 
                onClick={() => setSelectedRole(null)}
                className="btn-secondary py-2 px-4 text-xs"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Sovereign Values grid */}
      <section className="relative mx-auto max-w-5xl pb-24">
        <div className="w-full h-px bg-gradient-to-r from-transparent via-white/[0.06] to-transparent mb-20" />
        
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          {TOGETHER.map((item) => (
            <div
              key={item.title}
              className="card bg-gradient-to-b from-white/[0.03] to-white/[0.01] border-white/[0.04] p-4 text-center rounded-xl"
            >
              <span className="text-2xl select-none block mb-2">{item.emoji}</span>
              <h4 className="text-xs font-semibold text-white tracking-wide">
                {item.title}
              </h4>
              <p className="mt-1 text-[10px] text-[#8A8F98] leading-normal">
                {item.text}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* Call to Action & Contact Form Frame */}
      <section className="relative mx-auto max-w-4xl pb-16 space-y-6">
        <div className="card bg-gradient-to-b from-white/[0.08] to-white/[0.02] border-white/[0.08] p-8 md:p-10 text-center rounded-2xl relative overflow-hidden">
          <div className="absolute top-[-30%] left-[50%] translate-x-[-50%] w-[500px] h-[300px] rounded-full pointer-events-none opacity-20 bg-[radial-gradient(circle_at_center,rgba(94,106,210,0.25)_0%,transparent_70%)]" />
          
          <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-white">
            Connect the Logistics Ecosystem
          </h2>
          
          <p className="mx-auto mt-3.5 max-w-lg text-xs sm:text-sm text-[#8A8F98] leading-relaxed">
            Register your corporate credentials, run AI compliance moderators, and contribute to 
            operations discussion topics.
          </p>
          
          <div className="mt-8 flex justify-center">
            <Link to="/login" className="btn-primary px-6 py-2.5 shadow-[0_0_24px_rgba(94,106,210,0.3)]">
              Initialize Access
            </Link>
          </div>
        </div>

        {/* Integrated Landing Contact Us */}
        <div className="card bg-gradient-to-b from-white/[0.04] to-white/[0.01] border-white/[0.06] p-8 md:p-12 rounded-2xl relative overflow-hidden text-center shadow-[0_4px_30px_rgba(94,106,210,0.05)]">
          <div className="absolute inset-0 pointer-events-none opacity-20 bg-[radial-gradient(circle_at_center,rgba(94,106,210,0.15)_0%,transparent_70%)]" />
          
          <div className="relative z-10 max-w-2xl mx-auto flex flex-col items-center">
            <span className="text-[10px] font-semibold text-accent uppercase tracking-widest font-mono">
              Support Desk
            </span>
            <h2 className="text-xl sm:text-2xl font-bold tracking-tight text-white mt-2">
              Have questions or need operational assistance?
            </h2>
            <p className="text-xs text-[#8A8F98] mt-2 mb-8 max-w-lg">
              Get in touch with the CSCE administration. We typically respond within 24 hours.
            </p>
            
            <form onSubmit={handleContactSubmit} className="space-y-4 w-full max-w-xl text-left">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="label text-[9px] uppercase tracking-wider mb-1.5" htmlFor="landing-contact-name">Name</label>
                  <input
                    id="landing-contact-name"
                    className="input py-2 text-xs"
                    placeholder="Your Name"
                    value={contactName}
                    onChange={(e) => setContactName(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <label className="label text-[9px] uppercase tracking-wider mb-1.5" htmlFor="landing-contact-email">Email Address</label>
                  <input
                    id="landing-contact-email"
                    type="email"
                    className="input py-2 text-xs"
                    placeholder="name@organization.com"
                    value={contactEmail}
                    onChange={(e) => setContactEmail(e.target.value)}
                    required
                  />
                </div>
              </div>
              <div>
                <label className="label text-[9px] uppercase tracking-wider mb-1.5" htmlFor="landing-contact-message">Message</label>
                <textarea
                  id="landing-contact-message"
                  className="input text-xs min-h-[100px] resize-y"
                  placeholder="Describe your inquiry or logistics question..."
                  value={contactMessage}
                  onChange={(e) => setContactMessage(e.target.value)}
                  required
                  rows={4}
                />
              </div>
              
              {contactSuccess ? (
                <p className="text-xs text-emerald-400 bg-emerald-950/15 border border-emerald-500/25 p-3 rounded-lg font-medium animate-fade-in text-center w-full">
                  ✓ Thank you! Your message has been sent to our support desk.
                </p>
              ) : (
                <div className="pt-2 space-y-2">
                  <button type="submit" className="btn-primary w-full py-2.5 text-xs" disabled={submittingContact}>
                    {submittingContact ? "Sending…" : "Send Message"}
                  </button>
                  {contactError && (
                    <span className="block text-center text-xs text-red-400 font-medium">
                      ⚠️ {contactError}
                    </span>
                  )}
                </div>
              )}
            </form>
          </div>
        </div>
      </section>
    </div>
  );
}
