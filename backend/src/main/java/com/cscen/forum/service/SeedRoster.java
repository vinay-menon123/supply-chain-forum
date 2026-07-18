package com.cscen.forum.service;

import java.util.List;

/**
 * The cast of seeded community members, shared by {@link SeedService} (which
 * creates them) and {@link CommunityActivityService} (which keeps them talking).
 * Keeping the roster in one place means the daily activity job can never drift
 * from what was actually seeded.
 *
 * <p><b>Security note:</b> the moderator account is deliberately created with
 * <i>no</i> password hash - it exists only as the identity that marks answers as
 * verified. It must never be loggable-into, because this repository is public.
 */
public final class SeedRoster {

    private SeedRoster() {}

    /** memberType must be one of the six CSCEN types; topics is a CSV of question tags. */
    public record Member(String email, String username, String name, String memberType,
                         String org, String headline, String topics, String bio,
                         boolean mentor, boolean seeking) {}

    /** The moderator who verifies answers. Never gets a password. */
    public static final Member MODERATOR = new Member(
            "editorial.desk@cscen.example", "csce_desk", "CSCEN Editorial Desk", "PROFESSIONAL",
            "Centre for Supply Chain Excellence", "Community Moderator",
            "GENERAL,DIGITAL_AI,CAREERS",
            "The CSCEN editorial desk. We review answers from practitioners and mark the ones "
                    + "the community can rely on as verified.", false, false);

    public static final List<Member> MEMBERS = List.of(
            new Member("priya.sharma.scm@gmail.com", "priya_sharma", "Priya Sharma",
                    "PROFESSIONAL", "Mahindra Logistics", "Demand Planning Lead",
                    "DEMAND_PLANNING,DIGITAL_AI,INVENTORY",
                    "Ten years in demand planning across FMCG and 3PL. I care about forecast "
                            + "accuracy that survives contact with reality.", true, false),
            new Member("daniel.okafor.scm@gmail.com", "daniel_okafor", "Daniel Okafor",
                    "INDUSTRY_PARTNER", "Kontinental Freight", "Head of Logistics Operations",
                    "LOGISTICS,DIGITAL_AI,RISK",
                    "Running cross-border road and ocean freight ops. Pragmatic about tech - it "
                            + "has to move a KPI or it doesn't ship.", false, false),
            new Member("rahul.verma.ops@gmail.com", "rahul_verma", "Rahul Verma",
                    "PROFESSIONAL", "Delhivery", "Warehouse Operations Manager",
                    "WAREHOUSING,INVENTORY,DIGITAL_AI",
                    "I run a 300k sq ft multi-client fulfilment centre. Slotting, labour and "
                            + "peak planning are my daily reality.", false, false),
            new Member("ana.ferreira.sourcing@gmail.com", "ana_ferreira", "Ana Ferreira",
                    "PROFESSIONAL", "Nestle", "Strategic Sourcing Manager",
                    "PROCUREMENT,RISK,SUSTAINABILITY",
                    "Category and sourcing lead for packaging and logistics spend. Contracts, "
                            + "risk and supplier relationships are my world.", true, false),
            new Member("meera.iyer.phd@gmail.com", "meera_iyer", "Meera Iyer",
                    "ACADEMICIAN", "IIM Bangalore", "Professor of Supply Chain Management",
                    "CAREERS,DIGITAL_AI,SUSTAINABILITY",
                    "I research and teach supply chain strategy, and I mentor students moving "
                            + "into the profession.", true, false),
            new Member("arjun.nair.ct@gmail.com", "arjun_nair", "Arjun Nair",
                    "PROFESSIONAL", "Flipkart", "Control Tower Lead",
                    "LOGISTICS,DIGITAL_AI,RISK",
                    "I run the national control tower - exceptions, escalations and the 2am "
                            + "phone calls when a lane goes down.", true, false),
            new Member("sneha.kulkarni.sop@gmail.com", "sneha_kulkarni", "Sneha Kulkarni",
                    "PROFESSIONAL", "Hindustan Unilever", "S&OP Manager",
                    "DEMAND_PLANNING,INVENTORY,GENERAL",
                    "I chair the monthly S&OP cycle for a large FMCG portfolio. Consensus "
                            + "forecasting and the sales-finance-supply handshake.", false, false),
            new Member("vikram.singh.tpt@gmail.com", "vikram_singh", "Vikram Singh",
                    "PROFESSIONAL", "TCI Freight", "Transportation Manager",
                    "LOGISTICS,PROCUREMENT,RISK",
                    "Road freight, carrier contracts and lane cost models. I have opinions about "
                            + "detention charges.", false, false),
            new Member("fatima.sheikh.cold@gmail.com", "fatima_sheikh", "Fatima Sheikh",
                    "PROFESSIONAL", "Cipla", "Cold Chain Quality Lead",
                    "LOGISTICS,RISK,MANUFACTURING",
                    "Temperature-controlled pharma distribution. Validation, excursions and "
                            + "regulatory audits are my day job.", true, false),
            new Member("karthik.reddy.build@gmail.com", "karthik_reddy", "Karthik Reddy",
                    "STARTUP_TECH_PARTNER", "RouteIQ", "Founder & CTO",
                    "DIGITAL_AI,LOGISTICS,GENERAL",
                    "Building routing and visibility tooling for mid-market shippers. Ex-planner, "
                            + "so I try not to build things planners hate.", true, false),
            new Member("lakshmi.menon.inv@gmail.com", "lakshmi_menon", "Lakshmi Menon",
                    "PROFESSIONAL", "Reliance Retail", "Inventory Planning Analyst",
                    "INVENTORY,DEMAND_PLANNING,WAREHOUSING",
                    "Multi-echelon inventory across a large retail network. Safety stock, "
                            + "allocation and the eternal fight against dead stock.", false, false),
            new Member("joseph.mathew.proc@gmail.com", "joseph_mathew", "Joseph Mathew",
                    "PROFESSIONAL", "Ashok Leyland", "Head of Procurement",
                    "PROCUREMENT,MANUFACTURING,RISK",
                    "Direct materials procurement for commercial vehicles. Supplier development "
                            + "and should-cost modelling.", true, false),
            new Member("ritu.agarwal.esg@gmail.com", "ritu_agarwal", "Ritu Agarwal",
                    "PROFESSIONAL", "Tata Steel", "Supply Chain Sustainability Lead",
                    "SUSTAINABILITY,LOGISTICS,PROCUREMENT",
                    "Scope 3 emissions, supplier decarbonisation and making the numbers stand up "
                            + "to an auditor.", false, false),
            new Member("sameer.joshi.network@gmail.com", "sameer_joshi", "Sameer Joshi",
                    "PROFESSIONAL", "Independent Consultant", "Network Design Consultant",
                    "LOGISTICS,WAREHOUSING,GENERAL",
                    "I redesign distribution networks for a living - greenfield studies, DC "
                            + "footprints and the politics that come with closing a site.", true, false),
            new Member("nandini.rao.research@gmail.com", "nandini_rao", "Nandini Rao",
                    "RESEARCHER", "IIT Madras", "Doctoral Researcher, Supply Chain Analytics",
                    "DIGITAL_AI,SUSTAINABILITY,DEMAND_PLANNING",
                    "Researching ML for demand forecasting under disruption. Always looking for "
                            + "real-world datasets and practitioner sanity checks.", false, true),
            new Member("aditya.bose.student@gmail.com", "aditya_bose", "Aditya Bose",
                    "STUDENT", "NITIE Mumbai", "MBA Candidate, Operations",
                    "CAREERS,GENERAL,DIGITAL_AI",
                    "Final-year operations student trying to work out where the profession is "
                            + "actually heading before I pick a specialism.", false, true),
            new Member("grace.wanjiru.dist@gmail.com", "grace_wanjiru", "Grace Wanjiru",
                    "INDUSTRY_PARTNER", "Bidco Africa", "Regional Distribution Manager",
                    "WAREHOUSING,LOGISTICS,INVENTORY",
                    "Distribution across East African markets. Long lead times, thin "
                            + "infrastructure and very creative problem solving.", true, false),
            new Member("imran.qureshi.trade@gmail.com", "imran_qureshi", "Imran Qureshi",
                    "PROFESSIONAL", "DP World", "Customs & Trade Compliance Manager",
                    "RISK,LOGISTICS,PROCUREMENT",
                    "Customs, trade compliance and the paperwork that quietly decides whether "
                            + "your container moves today or next week.", false, false),
            new Member("divya.pillai.qcom@gmail.com", "divya_pillai", "Divya Pillai",
                    "PROFESSIONAL", "Zepto", "Quick Commerce Operations Lead",
                    "LOGISTICS,INVENTORY,DEMAND_PLANNING",
                    "Dark stores and 10-minute delivery. Everything I do is a trade-off between "
                            + "availability and waste.", false, false),
            new Member("thomas.varghese.fin@gmail.com", "thomas_varghese", "Thomas Varghese",
                    "PROFESSIONAL", "Godrej Consumer Products", "Supply Chain Finance Manager",
                    "PROCUREMENT,INVENTORY,GENERAL",
                    "Where the supply chain meets the P&L - working capital, cost-to-serve and "
                            + "why the cheapest freight rate is rarely the cheapest option.", false, false));

    /** Every seeded account, moderator included. */
    public static List<Member> all() {
        java.util.List<Member> out = new java.util.ArrayList<>(MEMBERS);
        out.add(MODERATOR);
        return out;
    }

    public static List<String> allEmails() {
        return all().stream().map(Member::email).toList();
    }
}
