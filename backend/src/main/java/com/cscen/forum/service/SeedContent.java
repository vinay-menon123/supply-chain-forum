package com.cscen.forum.service;

import java.util.List;

/**
 * The pool of practitioner-voice discussions the community runs on.
 *
 * <p>One ordered list, consumed from the front: {@link SeedService} back-dates the
 * first slice into a believable history, and {@link CommunityActivityService} posts
 * the next unused entry each day. Because both draw from the same list, a question
 * is never posted twice.
 */
public final class SeedContent {

    private SeedContent() {}

    /** tag must be one of QuestionService.TAGS. */
    public record Topic(String tag, String title, String body, List<String> answers) {}

    /** How many of the topics below are back-dated as the initial history. */
    public static final int BACKLOG_SIZE = 14;

    public static final List<Topic> TOPICS = List.of(

            new Topic("DIGITAL_AI",
                    "Is 'agentic AI' in supply chain planning actually delivering, or is it still mostly hype?",
                    """
                    Everywhere I look, 2026 is being called "the year of AI agents" - agents that \
                    autonomously re-run RFQs when a supplier misses a commitment, or rebook shipments \
                    when a lane gets disrupted. But I also keep seeing the warning that a large share \
                    of agentic-AI projects will get scrapped by 2027 over cost and unclear ROI.

                    For those of you who've actually piloted this: where is it genuinely earning its \
                    keep, and where did it fall over?""",
                    List.of(
                            """
                            We piloted an exception-resolution agent on inbound lanes last quarter. Honest \
                            verdict: it works, but only because we kept it on a tight leash. It recommends, a \
                            planner approves - full autonomy was a bridge too far for our data quality. Net was \
                            about 15 hours/week back to the team on chase-and-rebook busywork.

                            The real gate isn't the model, it's master data. If your supplier lead times and \
                            lane costs are dirty, the agent just makes confident wrong calls faster.""",
                            """
                            Same story on the warehouse side. We tried an agent for labour rebalancing across \
                            zones during peak. Great in simulation, but it kept over-reacting to short spikes \
                            and thrashing people between stations. We added a cooldown and a human sign-off and \
                            only then did it help. Start with a narrow, reversible decision.""",
                            """
                            My rule of thumb for anyone starting: pick ONE narrow workflow and instrument it \
                            against a human baseline for a full quarter. The projects that get scrapped are the \
                            ones that tried to automate the entire planning cycle on day one.""")),

            new Topic("DEMAND_PLANNING",
                    "How are you feeding external signals (weather, promos, macro) into demand forecasts without overfitting?",
                    """
                    Our classical stat models are stable but blind to the outside world. The pitch for \
                    ML-based forecasting is that it ingests external signals and continuously adjusts. \
                    My fear is we trade a boring, explainable baseline for a black box that chases noise.

                    How do you decide a signal is real and not just a spurious correlation that looked \
                    great in a backtest?""",
                    List.of(
                            """
                            Governance beats model choice here. We run champion/challenger: the classical \
                            baseline stays live alongside the ML model, so we can see exactly what the extra \
                            signals buy us. Any new external feature has to clear a 3-month rolling backtest AND \
                            improve forecast value-add, not just in-sample error.

                            Weather and promo lift are usually worth it. Macro indicators almost never survive \
                            the backtest for us - too lagged and too noisy at SKU level.""",
                            """
                            From the sourcing side, the promo signal is only as good as the promo calendar \
                            behind it. We got a big lift just by making marketing share locked promo plans with \
                            planning two weeks earlier. Cleaner input beat a fancier model.""",
                            """
                            Segment before you model. External signals help enormously on weather-sensitive and \
                            promo-driven SKUs, and do nothing for steady baseload items. Running one model over \
                            the whole portfolio is how you end up adding noise to the stable half.""")),

            new Topic("WAREHOUSING",
                    "WMS selection for a 50k-SKU 3PL: Manhattan vs Blue Yonder vs Korber - what actually matters?",
                    """
                    We're re-platforming the WMS for a multi-client fulfilment operation - roughly 50k \
                    active SKUs, e-commerce plus B2B, moderate budget. Every demo looks great; I want to \
                    hear from people who run these in production.

                    Specifically: how well does multi-client billing actually work, how painful was the \
                    implementation, and how much of the 'AI slotting' is real vs slideware?""",
                    List.of(
                            """
                            Having lived through a Manhattan Active rollout: the product is genuinely strong and \
                            the evergreen model is real, but total cost and implementation partner dependency are \
                            heavier than the sales cycle admits. For a mid-size 3PL, Korber tends to give you \
                            multi-client billing and 3PL-specific features out of the box with less customisation.

                            Weight multi-client billing and wave/waveless flexibility heavily - that's where 3PLs \
                            actually bleed margin, not on the AI slotting bullet points.""",
                            """
                            One thing that gets underweighted: the implementation partner matters more than the \
                            badge on the box. Two clients on the same WMS can have wildly different outcomes based \
                            on the SI. Ask each vendor for a reference customer in your exact vertical and volume \
                            band, and talk to them without the vendor in the room.""",
                            """
                            Budget for the integration layer, not just the WMS. Ours came in at nearly 40% of the \
                            programme cost once you count ERP, TMS, carrier APIs and the client portals. Nobody \
                            puts that on the comparison slide.""")),

            new Topic("PROCUREMENT",
                    "How are you structuring contracts for supplier price volatility right now?",
                    """
                    With input costs still swinging, the old fixed-price annual contract feels like \
                    gambling. I'm weighing index-linked pricing vs fixed price with a risk buffer vs \
                    shorter terms with more frequent re-negotiation.

                    What are you actually using, and how do you keep index-linked clauses from turning \
                    into a one-way ratchet in the supplier's favour?""",
                    List.of(
                            """
                            Index-linked, but always with a cap and a collar so it can't run away in either \
                            direction, plus a quarterly reopen window. The collar is the part people forget - it \
                            protects the supplier too, which makes them far more willing to sign.""",
                            """
                            Worth stepping back to total cost and relationship, not just the price clause. \
                            Dual-sourcing the volatile categories gives you a credible outside option, which does \
                            more for your effective price than any clause.""",
                            """
                            Whatever index you pick, write down exactly which published series, which lag, and \
                            who publishes the reconciliation. We lost six weeks arguing because "the steel index" \
                            turned out to mean two different things to the two parties.""")),

            new Topic("CAREERS",
                    "What should new supply chain grads learn for the AI era - beyond Excel?",
                    """
                    My students keep asking what makes them valuable if AI takes over routine planning \
                    and expediting. I have my own view, but I'd love to hear from practitioners who are \
                    actually hiring.

                    If you were starting today, what two or three skills would you build - and what's \
                    overrated?""",
                    List.of(
                            """
                            Data literacy plus judgment. Not coding necessarily, but the ability to read a model, \
                            know when its assumptions have broken, and have the confidence to override it and \
                            explain why. The planners who thrive are the ones the AI can't replace on the \
                            exception calls.""",
                            """
                            The commercial and negotiation side stays very human. Supplier relationships, \
                            influencing stakeholders, structuring a deal - AI drafts, people close. I'd take a \
                            grad who's sharp on negotiation and cost breakdowns over one who only knows tools.""",
                            """
                            Learn the physical operation first. Spend time on the floor of a warehouse or a \
                            yard. The best analysts I've hired understood why a pick path or a dock schedule is \
                            the way it is - the tech makes sense a lot faster once you've felt the constraint.""")),

            new Topic("INVENTORY",
                    "Safety stock by formula vs by segment: how do you stop the formula from lying to you?",
                    """
                    We compute safety stock with the classic service-level-times-sigma-times-root-lead-time \
                    formula, and for about a third of our SKUs it produces numbers nobody trusts. Slow \
                    movers get absurd cover, and the fast lines still stock out.

                    Are you overriding the formula, segmenting first, or replacing it entirely?""",
                    List.of(
                            """
                            The formula assumes normally distributed demand and a stable lead time. For slow, \
                            lumpy movers both assumptions are false, which is exactly where it blows up. We moved \
                            intermittent SKUs to a Croston-style method and left the formula on the fast, stable \
                            lines where it genuinely works.""",
                            """
                            Segment by demand variability AND lead-time variability before you pick a method. Most \
                            teams only segment by value (ABC) and then wonder why the maths misbehaves on the \
                            long-tail items.""",
                            """
                            Watch lead-time variability specifically - it usually dominates the term and it's the \
                            input people never update. Ours was still carrying a supplier lead time from two years \
                            before the supplier moved plants.""")),

            new Topic("LOGISTICS",
                    "Detention and demurrage: what actually works to bring it down?",
                    """
                    Our D&D spend has crept up to the point where finance is asking questions. Some of it \
                    is genuinely port congestion, but I suspect a good chunk is our own dwell and paperwork.

                    What has actually moved the number for you - appointment discipline, carrier terms, or \
                    something else?""",
                    List.of(
                            """
                            Start by splitting the bill into "our fault" and "not our fault" before you negotiate \
                            anything. We found nearly 60% was our own document delays and yard dwell, which no \
                            amount of carrier negotiation would have fixed.""",
                            """
                            Free-time terms are negotiable far more than people assume, especially if you can show \
                            volume and clean appointment adherence. We got two extra free days on our main lane \
                            simply by bringing data to the QBR.""",
                            """
                            Automate the document trigger. Most of our demurrage came from customs paperwork \
                            starting after arrival instead of before. Moving that upstream cut the bill by about a \
                            third without touching a single contract.""")),

            new Topic("RISK",
                    "How deep do you actually map your supply chain - tier 2, tier 3, or further?",
                    """
                    After a couple of nasty surprises where a tier-2 supplier we'd never heard of stopped a \
                    line, we're being asked to map deeper. The effort explodes past tier 2 and I'm not sure \
                    where the sensible stopping point is.

                    How far do you map, and how do you keep it current rather than a one-off consultant deck?""",
                    List.of(
                            """
                            Don't map everything - map the paths to your critical parts. We picked the top 20 \
                            components by line-stoppage impact and mapped those to raw material. That's a \
                            tractable piece of work and it caught two single points of failure.""",
                            """
                            Keeping it current is the hard part. We made tier-2 disclosure a contractual \
                            requirement at renewal, with an annual refresh. Without a contractual hook it decays \
                            into fiction within a year.""",
                            """
                            Geography matters more than tiers. Two "different" suppliers sitting in the same \
                            industrial park behind the same power grid is a concentration risk your tier map won't \
                            show unless you plot it on a map.""")),

            new Topic("SUSTAINABILITY",
                    "Scope 3 logistics emissions: how are you getting numbers an auditor will accept?",
                    """
                    We report Scope 3 transport emissions using spend-based factors, and our auditor has \
                    started pushing back because the numbers move with freight rates rather than with \
                    actual activity.

                    Who has moved to activity-based (tonne-km) reporting, and how did you get the data out \
                    of your carriers?""",
                    List.of(
                            """
                            Spend-based is fine as a starting inventory and indefensible as a reduction story - if \
                            rates fall your emissions "drop" while nothing changed physically. Moving to tonne-km \
                            on your top lanes first gets you most of the accuracy for a fraction of the work.""",
                            """
                            Getting carrier data is a contracting problem, not a technical one. We added an \
                            emissions data clause at renewal - format, frequency, and a GLEC-aligned methodology. \
                            Carriers who wanted the volume complied.""",
                            """
                            Be careful comparing year on year after a methodology change. We restated the prior \
                            year on the new basis and published both, which is the only way the trend line means \
                            anything.""")),

            new Topic("MANUFACTURING",
                    "Line stoppage from a single stranded truck - what does a serious recovery playbook look like?",
                    """
                    We had a JIT inbound truck break down four hours from the plant and it very nearly cost \
                    us a shift. The recovery was pure improvisation and phone calls.

                    What does a genuinely good playbook contain? I want something the shift lead can execute \
                    at 3am without a committee.""",
                    List.of(
                            """
                            Pre-agree the decisions, not just the contacts. Ours defines the spend authority per \
                            severity tier, so the shift lead can authorise an expedite up to a set value without \
                            waking anybody. That single change cut our reaction time more than any tool.""",
                            """
                            Know your line-down cost per hour before the event. It is the number that makes the \
                            expedite decision obvious - once people can compare a 40k expedite against a 300k \
                            stoppage, the argument disappears.""",
                            """
                            Keep a live list of the nearest alternate stock, not just alternate carriers. In our \
                            case a sister plant 90 minutes away had the part and nobody thought to check for two \
                            hours.""")),

            new Topic("DIGITAL_AI",
                    "Control tower software: are the big platforms worth it, or is it a data-integration project wearing a UI?",
                    """
                    We're being quoted serious money for a visibility/control tower platform. Internally the \
                    debate is whether the value is in the product or in the integration work we'd have to do \
                    anyway.

                    For those who've implemented one - what did you actually get that you couldn't have built \
                    on top of your existing TMS?""",
                    List.of(
                            """
                            The honest answer is that 80% of the value was the integration and data model, which \
                            we did have to do ourselves regardless. What the platform bought us was the carrier \
                            connectivity library - hundreds of pre-built integrations we were never going to \
                            maintain.""",
                            """
                            Visibility on its own changes nothing. The value appears when you attach decisions to \
                            it - a recommendation with a cost attached, routed to someone who can act. A dashboard \
                            that shows you a late truck you can't do anything about is just anxiety as a service.""",
                            """
                            Ask the vendor how many of your specific carriers are already integrated, by name. The \
                            answer is usually far lower than the marketing number, and the gap becomes your \
                            project.""")),

            new Topic("WAREHOUSING",
                    "Goods-to-person automation: at what volume does it actually pay back?",
                    """
                    We're evaluating AMR-based goods-to-person for a fulfilment site. Vendor payback models \
                    all say 3 years, which I don't believe because they assume peak volumes every day.

                    At what order profile and volume did it genuinely pay back for you, and what did the \
                    model miss?""",
                    List.of(
                            """
                            Our payback landed at just over four years, not the three we were sold, and the gap \
                            was almost entirely volume seasonality - the robots idle in the trough months but the \
                            lease doesn't. Model your actual monthly curve, not your peak.""",
                            """
                            It paid back fastest not on labour but on space. We deferred a new building by \
                            densifying storage, and that avoided-capex line was worth more than the picking \
                            productivity gain.""",
                            """
                            Watch the SKU profile. Goods-to-person shines on small, slow-moving, many-line orders. \
                            If you're shipping full cases of a few fast movers, conventional pick paths will beat \
                            it comfortably.""")),

            new Topic("DEMAND_PLANNING",
                    "New product forecasting with no history - what actually works?",
                    """
                    We launch a lot of line extensions and every launch forecast is essentially a negotiation \
                    between marketing optimism and supply chain caution. We usually end up either scrapping \
                    stock or missing the launch window.

                    What method has actually worked for you on genuinely new items?""",
                    List.of(
                            """
                            Analogue forecasting with a disciplined library - pick 3 to 5 comparable past launches \
                            and use the shape of their first 13 weeks, not a single guessed number. The discipline \
                            is in agreeing the analogues BEFORE anyone states a target.""",
                            """
                            Forecast the shape, commit to the volume late. We build postponement into the launch: \
                            common components ordered early, final pack configuration decided as close to launch \
                            as the lead time allows.""",
                            """
                            Track and publish launch forecast error by owner. Once marketing saw their own launch \
                            accuracy trend in the S&OP pack, the optimism moderated considerably without a single \
                            argument.""")),

            new Topic("LOGISTICS",
                    "Quick commerce dark stores: how do you keep availability high without drowning in waste?",
                    """
                    Running 10-minute delivery from dark stores means every store is a tiny warehouse with \
                    almost no buffer. Push too little and availability tanks; push too much and fresh items \
                    get written off.

                    How are you setting the assortment and replenishment cadence to keep both in check?""",
                    List.of(
                            """
                            Assortment discipline beats clever replenishment. We cut the tail hard - the bottom \
                            30% of SKUs by units drove most of the write-off and almost none of the basket. \
                            Availability on the top 200 lines is what customers actually notice.""",
                            """
                            Multiple small replenishments beat one big one, even though the freight cost per unit \
                            is worse. Shorter cycles mean smaller forecast error to absorb, and the waste saving \
                            more than paid for the extra trips.""",
                            """
                            Set service level by shelf life, not by margin. Short-life items need a lower target \
                            fill rate or you are simply choosing to write stock off. That felt wrong commercially \
                            until we costed it properly.""")),

            new Topic("PROCUREMENT",
                    "Should-cost modelling: how much detail is actually worth building?",
                    """
                    We want to move negotiations away from "give us 5%" toward a real cost breakdown. But \
                    building should-cost models for a big category is a serious effort and I'm wary of \
                    spending months to be argued out of it in one meeting.

                    How granular do your models get, and how do suppliers react?""",
                    List.of(
                            """
                            Material, conversion, logistics, overhead and margin is enough for most categories. \
                            Going deeper than that produces precision you can't defend and the argument moves to \
                            your assumptions instead of their price.""",
                            """
                            The reaction depends entirely on how you open. Framed as "help us understand where our \
                            model is wrong", suppliers engage and often teach you something. Framed as "our model \
                            says you're overcharging", they lawyer up.""",
                            """
                            Build it for the categories where material is the dominant cost and the index is \
                            public. That's where the model is both cheap to build and hard to argue with.""")),

            new Topic("INVENTORY",
                    "How do you actually get rid of dead and slow-moving stock without destroying margin?",
                    """
                    We're carrying a meaningful amount of stock that hasn't moved in over a year. Everyone \
                    agrees it should go, nobody wants to book the write-down, so it just sits there consuming \
                    space and working capital.

                    What has worked - and how did you get finance comfortable?""",
                    List.of(
                            """
                            The blocker is almost always accounting, not operations. We got moving by agreeing a \
                            provisioning policy up front - stock past X months is provisioned automatically - so \
                            the write-down stops being a discrete decision someone has to own.""",
                            """
                            Cost out the carrying, don't just look at the write-down. Once you put space, handling, \
                            insurance and obsolescence risk against it, holding for a hoped-for future order is \
                            usually the more expensive choice.""",
                            """
                            Set an ageing trigger with a defined action ladder - internal transfer, then discount, \
                            then bundle, then liquidate - with dates. Without the ladder it just becomes a monthly \
                            report nobody acts on.""")),

            new Topic("RISK",
                    "Is dual sourcing worth the cost, or is it insurance you never claim?",
                    """
                    Finance sees dual sourcing as a permanent margin give-up: lost volume leverage, extra \
                    qualification cost, more complexity. Supply chain sees it as the thing that saves us in a \
                    crisis. Both are right.

                    How do you decide which categories genuinely justify it?""",
                    List.of(
                            """
                            Treat it as an option and price it. Expected cost of disruption times probability, \
                            against the annual premium of the second source. For most C-parts the maths says don't \
                            bother; for anything that stops a line it says do it immediately.""",
                            """
                            You don't need a 50/50 split. A qualified second source at 10% of volume keeps the \
                            relationship warm and the qualification current at a fraction of the leverage cost - \
                            and it's a credible threat in negotiation, which pays for itself.""",
                            """
                            Qualified-but-dormant is a trap. If the second source hasn't run your part in 18 \
                            months, treat them as unqualified. We learned that the expensive way.""")),

            new Topic("CAREERS",
                    "Moving from operations into supply chain strategy - how did you make the jump?",
                    """
                    I've spent six years in warehouse and transport operations and I'm good at it, but I keep \
                    getting told I'm "too operational" for network design and strategy roles.

                    For those who made this transition - what actually got you across?""",
                    List.of(
                            """
                            Ops experience is an asset, not a liability - the problem is usually how it's framed. \
                            Stop describing what you ran and start describing what you decided and what it was \
                            worth. "Redesigned the dock schedule" becomes "cut 1.2 crore of detention".""",
                            """
                            Get one modelling credential or project under your belt so the screening filter has \
                            something to catch on. A network study, even a small internal one, changes how the CV \
                            reads immediately.""",
                            """
                            Volunteer for the cross-functional projects nobody wants - the S&OP redesign, the DC \
                            relocation study. That's where strategy people see you work, and internal moves are \
                            far easier than external ones.""")),

            new Topic("SUSTAINABILITY",
                    "Modal shift from road to rail: what stopped it from working for you?",
                    """
                    On paper, shifting our long-haul trunk routes to rail cuts emissions dramatically and \
                    costs less per tonne-km. In practice every attempt we've made has died on reliability or \
                    first/last-mile cost.

                    Who has made this stick, and what did it take?""",
                    List.of(
                            """
                            It works where you can restructure the whole flow around it, not bolt it onto a \
                            road-shaped network. That usually means bigger, less frequent shipments and more \
                            inventory at the destination - which is a working-capital decision, not a transport \
                            one.""",
                            """
                            Terminal handling and drayage ate our savings. The line-haul rate looked great and the \
                            door-to-door number was barely better than road, with worse variability.""",
                            """
                            Match it to the right cargo. Non-urgent, dense, stable-demand items are perfect. \
                            Anything with a tight promise window or short shelf life is not, and forcing it will \
                            just teach the business that rail doesn't work.""")),

            new Topic("GENERAL",
                    "What is the most useful supply chain KPI nobody in your business looks at?",
                    """
                    We track OTIF, fill rate, inventory turns and cost per case like everyone else. I'm \
                    increasingly convinced the metrics that would actually change behaviour are the ones we \
                    don't report.

                    What's the underrated metric that changed how your team worked?""",
                    List.of(
                            """
                            Schedule adherence at the supplier, measured in days early as well as days late. \
                            Everyone chases lateness; nobody notices that chronic early delivery is quietly \
                            funding itself out of your working capital.""",
                            """
                            Cost to serve by customer. It's uncomfortable because it usually shows a chunk of \
                            revenue is unprofitable once you load freight and handling properly - which is exactly \
                            why it changes behaviour.""",
                            """
                            Forecast value-add. If your "improved" forecast is worse than a naive one, you're \
                            paying a team to add noise. It's the only metric that ever made our planners change \
                            method.""")),

            new Topic("DIGITAL_AI",
                    "Digital twin of a distribution network: genuinely useful or an expensive simulation?",
                    """
                    We're being pitched a digital twin of our distribution network for scenario planning. It \
                    looks impressive in the demo, but I've seen simulation projects become shelfware once the \
                    consultants leave.

                    Has anyone kept one alive and used it for real decisions?""",
                    List.of(
                            """
                            Ours survived because we tied it to a recurring decision - the annual network review \
                            and every major customer onboarding. A twin with no scheduled question to answer dies \
                            within a year, guaranteed.""",
                            """
                            The maintenance burden is the thing to plan for. If the model isn't refreshed as the \
                            network changes, it silently becomes wrong, and one bad recommendation destroys trust \
                            permanently.""",
                            """
                            Start with a narrow twin of the decision you actually re-make often, not the whole \
                            network. Ours began as a DC-sizing model and only expanded once people trusted it.""")),

            new Topic("LOGISTICS",
                    "Carrier RFQ: how often should you go to market without wrecking your relationships?",
                    """
                    Procurement wants an annual full RFQ on road freight. Our transport team says that burns \
                    goodwill and we end up with cheap carriers who fail us at peak.

                    What cadence works, and how do you keep incumbents honest without a full tender?""",
                    List.of(
                            """
                            Full tender every 2-3 years, with an annual index-based rate review in between. \
                            Annual full tenders train carriers to bid low and claw it back in accessorials.""",
                            """
                            Keep a small share of volume on a rolling spot benchmark - maybe 10%. It tells you \
                            what the market is doing continuously without putting the whole book in play.""",
                            """
                            Score on more than rate or you will keep buying failure. Ours weights on-time \
                            performance, claims and tender acceptance, and we publish the scorecard to the \
                            carriers. Behaviour changed more from the scorecard than from the tender.""")),

            new Topic("WAREHOUSING",
                    "Peak season labour: how are you covering it without burning your permanent team out?",
                    """
                    Every peak we lean on temps and overtime, quality dips, and we lose good permanent people \
                    in January. The economics of over-hiring permanently don't work either.

                    What staffing model has actually held up for you?""",
                    List.of(
                            """
                            Cross-train early and hard. The constraint at peak is rarely total headcount, it's \
                            headcount qualified for the bottleneck station. We now train year-round for peak \
                            roles, which costs little and removes most of the crunch.""",
                            """
                            Bring temps in three weeks before you need them, not on the first big day. Productivity \
                            of a trained temp is roughly double an untrained one and the error rate is a fraction \
                            - it pays for the early start easily.""",
                            """
                            Protect the permanent team's rest deliberately, on a roster, and treat it as \
                            non-negotiable. The January attrition costs far more than the overtime you saved.""")),

            new Topic("PROCUREMENT",
                    "Supplier scorecards: how do you make them change behaviour rather than just generate reports?",
                    """
                    We have a supplier scorecard with about 20 metrics. Suppliers acknowledge it politely and \
                    nothing changes. It feels like an administrative exercise on both sides.

                    What made scorecards actually bite for you?""",
                    List.of(
                            """
                            Cut it to five metrics that the supplier can actually control and that you will act \
                            on. Twenty metrics tells the supplier you have no priorities, so they pick their own.""",
                            """
                            Attach a consequence and a reward. Ours ties the top band to preferential volume \
                            allocation on new business. The moment the scorecard affected the order book, the \
                            quarterly review meetings got a lot more attentive.""",
                            """
                            Show them their rank band, anonymised, against peers. Competitive pressure did more \
                            for our on-time performance than any penalty clause.""")),

            new Topic("RISK",
                    "How are you handling compliance risk on cross-border shipments as rules keep shifting?",
                    """
                    Customs classifications, sanctions lists and documentation requirements seem to move \
                    constantly, and a single misclassification can hold a container for weeks.

                    How are you keeping up without hiring a full compliance department?""",
                    List.of(
                            """
                            Get classification right once, centrally, and lock it. Most of the errors I see come \
                            from a local team re-deciding an HS code per shipment. A governed master with a review \
                            cycle removes the whole class of problem.""",
                            """
                            Screen at order entry, not at dispatch. Catching a restricted party when the container \
                            is already at the port is the expensive version of the same check.""",
                            """
                            Build the relationship with your broker before you need it. When rules shift, the \
                            brokers tell their engaged clients first - that informal early warning has been worth \
                            more to us than any subscription service.""")),

            new Topic("DEMAND_PLANNING",
                    "Consensus S&OP: how do you stop it becoming a number-negotiation meeting?",
                    """
                    Our monthly consensus meeting has degenerated into sales defending their number and \
                    supply chain defending theirs, with the outcome decided by whoever is most senior in the \
                    room.

                    How do you keep it a decision forum rather than a negotiation?""",
                    List.of(
                            """
                            Separate the unconstrained demand plan from the commercial target, explicitly and in \
                            writing. Most S&OP dysfunction comes from trying to make one number serve both \
                            purposes.""",
                            """
                            Bring accuracy history to every meeting. When each function's own forecast bias is on \
                            the wall, the debate shifts from opinion to evidence very quickly.""",
                            """
                            Make the meeting decide exceptions only, with everything within tolerance auto-approved \
                            beforehand. Ours went from three hours to fifty minutes and the quality of discussion \
                            went up.""")),

            new Topic("GENERAL",
                    "Cost to serve: how did you build it, and what did it change?",
                    """
                    We price roughly on volume and gut feel. I suspect some of our largest customers are our \
                    least profitable once you load delivery frequency, drop size and returns properly.

                    Who has built a real cost-to-serve model, and what changed as a result?""",
                    List.of(
                            """
                            Start coarse. Allocate freight, handling and returns by actual drop and line counts, \
                            not by revenue share. Even a rough version usually reveals the loss-making tail \
                            immediately, and precision can come later.""",
                            """
                            The uncomfortable part is what you do next. We didn't fire customers - we changed the \
                            terms: minimum order values, fewer delivery days, pallet incentives. Most customers \
                            accepted, and the ones who didn't were the ones we were subsidising most.""",
                            """
                            Get finance to co-own it from day one. A cost-to-serve model that supply chain built \
                            alone gets argued out of existence the moment it threatens a commercial \
                            relationship.""")),

            new Topic("MANUFACTURING",
                    "Postponement: where does it genuinely pay off in practice?",
                    """
                    The theory of holding generic stock and configuring late is appealing, but every practical \
                    attempt we've made has run into packaging line constraints or extra handling cost.

                    Where has postponement genuinely worked for you?""",
                    List.of(
                            """
                            It pays where variety is high and the differentiating step is cheap and late - \
                            labelling, language packs, kitting. It fails where the differentiation is baked into \
                            the process early, and no amount of design will change that.""",
                            """
                            The gain is variance pooling, so it's worth most on items with high forecast error at \
                            the variant level but stable demand in aggregate. Run that calculation before you \
                            invest in the capability.""",
                            """
                            Watch the handling cost honestly. We saved a lot of inventory and gave a chunk of it \
                            straight back in extra touches. Still net positive, but a fraction of the business \
                            case we approved.""")),

            new Topic("INVENTORY",
                    "Multi-echelon inventory optimisation: worth it, or is single-echelon plus good judgment enough?",
                    """
                    We're being pitched MEIO tooling. Our network is a national DC feeding regional DCs \
                    feeding stores, and today each echelon plans its own safety stock more or less \
                    independently.

                    Did MEIO deliver for you, or did the complexity outweigh the gain?""",
                    List.of(
                            """
                            The gain is real and it comes from putting buffer where it's cheapest to hold rather \
                            than everywhere. We took out around 15% of total network stock at the same service \
                            level, which independent echelon planning simply cannot find.""",
                            """
                            It demands data discipline that most networks don't have - accurate lead times BETWEEN \
                            echelons, not just from suppliers. That was six months of cleanup before the tool \
                            produced anything trustworthy.""",
                            """
                            Watch the change management. MEIO will tell a regional DC manager to hold less stock \
                            and trust the node above. If they don't believe the replenishment will arrive, they \
                            will quietly override it and you'll get nothing.""")),

            new Topic("CAREERS",
                    "Is a supply chain certification (CSCP, CPIM, CIPS) worth it mid-career?",
                    """
                    I'm eight years in, mostly operational, and considering a certification. It's a real \
                    investment of time and money and I can't tell whether hiring managers genuinely value it \
                    or just tick a box.

                    Has it made a concrete difference for anyone here?""",
                    List.of(
                            """
                            It rarely wins you a job on its own, but it reliably gets you past the screening \
                            filter and it fills gaps you didn't know you had. I found the value was less the \
                            certificate and more finally understanding the bits of the chain I'd never worked \
                            in.""",
                            """
                            Match it to direction. CPIM if you're heading into planning and manufacturing, CIPS \
                            if you're going commercial and procurement. The generic ones are less useful \
                            mid-career because you already have the breadth.""",
                            """
                            If your employer will fund it, take it. If you're self-funding mid-career, a specific \
                            skill - a planning system, a modelling tool - often converts to salary faster.""")));
}
