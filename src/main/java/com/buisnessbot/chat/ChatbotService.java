package com.buisnessbot.chat;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ChatbotService {

  private static final Pattern BUDGET_PATTERN = Pattern.compile("(?:rs\\.?|inr)?\\s?(\\d{4,7})",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern EMAIL_PATTERN = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
  private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+91[-\\s]?)?[6-9]\\d{9}");
  private static final Locale INDIA = Locale.of("en", "IN");

  private final Map<String, LeadSnapshot> leads = new ConcurrentHashMap<>();
  private final Map<String, List<ChatTurn>> conversations = new ConcurrentHashMap<>();
  private final OpenAiChatService openAiChatService;
  private final List<Plan> plans = List.of(
      new Plan("Starter", "Rs. 9,999", "landing page + contact flow"),
      new Plan("Growth", "Rs. 24,999", "website + chatbot + analytics"),
      new Plan("Scale", "Rs. 49,999+", "custom automation + CRM integration"));

  public ChatbotService(OpenAiChatService openAiChatService) {
    this.openAiChatService = openAiChatService;
  }

  public ChatResponse reply(ChatRequest request) {
    LeadSnapshot lead = leads.computeIfAbsent(request.sessionId(), ignored -> new LeadSnapshot());
    List<ChatTurn> history = conversations.computeIfAbsent(request.sessionId(), ignored -> new ArrayList<>());
    updateLead(lead, request.message());

    String lower = request.message().toLowerCase(Locale.ROOT).trim();
    String reply = openAiChatService.reply(request.message(), lead, List.copyOf(history))
        .orElseGet(() -> buildReply(lower, lead));

    remember(history, new ChatTurn("user", request.message()));
    remember(history, new ChatTurn("assistant", reply));
    return new ChatResponse(reply, lead);
  }

  private void remember(List<ChatTurn> history, ChatTurn turn) {
    history.add(turn);
    if (history.size() > 20) {
      history.remove(0);
    }
  }

  private void updateLead(LeadSnapshot lead, String message) {
    String lower = message.toLowerCase(Locale.ROOT);

    if (lower.contains("website")) {
      lead.setService("Website");
    }
    if (lower.contains("chatbot") || lower.contains("bot")) {
      lead.setService("AI Chatbot");
    }
    if (lower.contains("automation")) {
      lead.setService("Automation");
    }
    if (lower.contains("restaurant")) {
      lead.setBusinessType("Restaurant");
    }
    if (lower.contains("clinic")) {
      lead.setBusinessType("Clinic");
    }
    if (lower.contains("shop") || lower.contains("store")) {
      lead.setBusinessType("Retail shop");
    }

    Matcher budgetMatcher = BUDGET_PATTERN.matcher(message);
    if (budgetMatcher.find()) {
      long amount = Long.parseLong(budgetMatcher.group(1));
      lead.setBudget("Rs. " + NumberFormat.getIntegerInstance(INDIA).format(amount));
    }

    Matcher emailMatcher = EMAIL_PATTERN.matcher(message);
    if (emailMatcher.find()) {
      lead.setContact(emailMatcher.group());
    }

    Matcher phoneMatcher = PHONE_PATTERN.matcher(message);
    if (phoneMatcher.find()) {
      lead.setContact(phoneMatcher.group());
    }
  }

  private String buildReply(String message, LeadSnapshot lead) {
    if (isGreeting(message)) {
      return greetingReply();
    }

    if (isSmallTalk(message)) {
      return smallTalkReply();
    }

    if (isHelpRequest(message)) {
      return helpReply();
    }

    if (isVague(message)) {
      return interactiveStarterReply();
    }

    if (containsAny(message, "idea generator", "business idea", "new idea", "startup idea")) {
      return businessIdeaGenerator(lead);
    }

    if (containsAny(message, "market research", "market size", "target market", "research assistant")) {
      return marketResearchAssistant(lead);
    }

    if (containsAny(message, "competitor", "competition", "competitive analysis")) {
      return competitorAnalysis(lead);
    }

    if (containsAny(message, "business plan", "plan generator")) {
      return businessPlanGenerator(lead);
    }

    if (containsAny(message, "sales strategy", "marketing strategy", "sales and marketing", "go to market")) {
      return salesMarketingStrategy(lead);
    }

    if (containsAny(message, "startup cost", "cost calculator", "startup budget")) {
      return startupCostCalculator(lead);
    }

    if (containsAny(message, "pitch deck", "deck content", "investor pitch")) {
      return pitchDeckGenerator(lead);
    }

    if (containsAny(message, "customer persona", "persona builder", "buyer persona")) {
      return customerPersonaBuilder(lead);
    }

    if (containsAny(message, "swot", "strength weakness", "swot analysis")) {
      return swotAnalysis(lead);
    }

    if (containsAny(message, "price", "pricing", "plan")) {
      return "Hamare current packages:\n\n"
          + plans.stream()
              .map(plan -> plan.name() + ": " + plan.price() + " - " + plan.fit())
              .reduce((first, second) -> first + "\n" + second)
              .orElse("")
          + "\n\nAap business type aur goal batao, main best plan suggest kar dunga.";
    }

    if (containsAny(message, "estimate", "budget", "cost")) {
      Plan plan = recommendPlan(lead);
      return "Initial estimate ke hisaab se " + plan.name() + " plan fit lag raha hai.\n\n"
          + plan.name() + ": " + plan.price() + "\n"
          + "Best for: " + plan.fit() + "\n\n"
          + "Lead snapshot:\n" + leadSummary(lead);
    }

    if (containsAny(message, "book", "call", "consult")) {
      return "Great. Consultation book karne ke liye apna phone number ya email bhej do.\n\n"
          + "Main ye details ready rakhunga:\n" + leadSummary(lead);
    }

    if (containsAny(message, "service", "website", "chatbot", "automation")) {
      return "BUISNESS BOT business websites, AI chatbots, lead generation, automation setup me help karta hai.\n\n"
          + "Agar aap goal bata do jaise \"more leads\", \"online booking\", ya \"customer support automation\", "
          + "main exact flow recommend karunga.";
    }

    if (!lead.getContact().isBlank()) {
      String service = lead.getService().isBlank() ? "business growth" : lead.getService();
      return "Perfect, contact save ho gaya: " + lead.getContact() + "\n\n"
          + "Next step: main aapke liye " + service + " requirement ka short brief ready kar sakta hoon.\n"
          + leadSummary(lead);
    }

    if (isBusinessConcern(message)) {
      return concernAdvisor(message, lead);
    }

    return generalAssistantReply(message);
  }

  private String concernAdvisor(String message, LeadSnapshot lead) {
    String concern = detectConcern(message);
    String businessType = valueOrFallback(lead.getBusinessType(), "your business");
    String budget = valueOrFallback(lead.getBudget(), "not shared");

    return "Best Answer\n\n"
        + answerForConcern(concern, businessType) + "\n\n"
        + "Why this matters:\n"
        + explanationForConcern(concern) + "\n\n"
        + "Action plan:\n"
        + actionPlanForConcern(concern) + "\n\n"
        + "What I understood:\n"
        + "- Concern: " + concern + "\n"
        + "- Business type: " + businessType + "\n"
        + "- Budget: " + budget + "\n\n"
        + "Reply with your business type, city, target customer, and budget. I will make this answer more specific.";
  }

  private boolean isGreeting(String message) {
    String clean = message.replaceAll("[^a-zA-Z]", "").toLowerCase(Locale.ROOT);
    return clean.equals("hi")
        || clean.equals("hello")
        || clean.equals("hey")
        || clean.equals("hii")
        || clean.equals("helo")
        || clean.equals("namaste")
        || clean.equals("namaskar");
  }

  private boolean isSmallTalk(String message) {
    String clean = message.replaceAll("[^a-zA-Z ]", "").toLowerCase(Locale.ROOT).trim();
    return clean.equals("how are you")
        || clean.equals("how r u")
        || clean.equals("how are u")
        || clean.equals("kaise ho")
        || clean.equals("kese ho")
        || clean.equals("how is it going")
        || clean.equals("whats up")
        || clean.equals("sup");
  }

  private boolean isHelpRequest(String message) {
    return containsAny(message,
        "help",
        "madad",
        "karenga",
        "karega",
        "karoge",
        "meri help",
        "mujhe help",
        "can you help",
        "please help");
  }

  private boolean isVague(String message) {
    String clean = message.replaceAll("[^a-zA-Z0-9 ]", "").trim();
    return clean.length() < 12 && !containsAny(clean.toLowerCase(Locale.ROOT), "cost", "plan", "idea", "swot");
  }

  private boolean isBusinessConcern(String message) {
    return containsAny(message,
        "business",
        "startup",
        "customer",
        "lead",
        "client",
        "sale",
        "market",
        "marketing",
        "competitor",
        "price",
        "pricing",
        "fund",
        "money",
        "profit",
        "shop",
        "store",
        "clinic",
        "restaurant",
        "idea",
        "cost",
        "budget",
        "plan",
        "pitch",
        "swot",
        "growth",
        "brand",
        "ads",
        "seo");
  }

  private String greetingReply() {
    return "Hi bhai! Main BUISNESS BOT hoon.\n\n"
        + "Apni business concern type karo, main best answer, explanation, aur action plan dunga.\n\n"
        + "Examples:\n"
        + "- Mere shop me customers nahi aa rahe\n"
        + "- Mujhe business idea chahiye\n"
        + "- Competitors strong hain, kya karu?\n"
        + "- Startup ka cost calculate karo\n"
        + "- Pitch deck content bana do";
  }

  private String smallTalkReply() {
    return "Main badhiya hoon bhai, ready to help.\n\n"
        + "Tum bas apni business situation batao. Example:\n"
        + "- Business type kya hai?\n"
        + "- Problem kya chal rahi hai?\n"
        + "- Budget ya target kya hai?\n\n"
        + "Agar quick start chahiye, bolo: \"Mere business ke liye plan bana do.\"";
  }

  private String helpReply() {
    return "Haan bhai, bilkul help karunga.\n\n"
        + "Bas apna question ya problem clearly likh do. Main simple language me answer, explanation, aur next steps dunga.\n\n"
        + "Agar business help chahiye to ye format best hai:\n"
        + "\"Mera business [type] hai, problem [issue] hai, budget [amount] hai.\"\n\n"
        + "Agar general question hai to seedha pooch lo, jaise:\n"
        + "- Java Spring Boot kaise run karu?\n"
        + "- Marketing strategy kaise banau?\n"
        + "- Mere idea ko improve karo";
  }

  private String interactiveStarterReply() {
    return "Bhai mujhe thoda context do, phir main sharp answer dunga.\n\n"
        + "Choose one ya apna question type karo:\n"
        + "1. Business idea chahiye\n"
        + "2. Customers nahi aa rahe\n"
        + "3. Competitors strong hain\n"
        + "4. Pricing confusion hai\n"
        + "5. Startup cost calculate karna hai\n\n"
        + "Best format: \"Mera [business type] hai, problem [problem] hai, budget [amount] hai.\"";
  }

  private String generalAssistantReply(String message) {
    if (containsAny(message, "what is java", "java kya hai", "java kya hota hai")) {
      return "Java ek programming language hai jo apps, websites ke backend, Android apps, enterprise software, aur APIs banane ke kaam aati hai.\n\n"
          + "Simple words me:\n"
          + "1. Java code likhte ho.\n"
          + "2. Java us code ko run karne layak banata hai.\n"
          + "3. Same Java app Windows, Mac, Linux par chal sakti hai agar Java installed ho.\n\n"
          + "Spring Boot Java ka popular framework hai. Isse backend APIs aur web apps fast ban jati hain, jaise tumhara BUISNESS BOT.";
    }

    if (containsAny(message, "what is spring boot", "springboot kya hai", "spring boot kya hai")) {
      return "Spring Boot Java ka framework hai jo backend app banana easy karta hai.\n\n"
          + "Isme tum:\n"
          + "1. REST API bana sakte ho.\n"
          + "2. Database connect kar sakte ho.\n"
          + "3. Static frontend serve kar sakte ho.\n"
          + "4. Security, config, logging easily manage kar sakte ho.\n\n"
          + "Tumhare BUISNESS BOT me Spring Boot `/api/chat` endpoint run kar raha hai, aur frontend us endpoint ko message bhej raha hai.";
    }

    if (containsAny(message, "simple words", "easy words", "basic explanation", "beginner")) {
      return "Bilkul bhai, simple words me samjhaunga.\n\n"
          + "Topic ya question bhejo, main answer ko 3 parts me tod dunga:\n"
          + "1. Ye kya hai\n"
          + "2. Ye kaam kaise karta hai\n"
          + "3. Real example\n\n"
          + "Example: \"Spring Boot simple words me samjhao\"";
    }

    if (containsAny(message, "how to", "kaise karu", "kaise kare", "kaise banau", "kaise banao")) {
      return "Haan bhai, step-by-step bata sakta hoon.\n\n"
          + "Tumne poocha: \"" + message + "\"\n\n"
          + "Best tareeka:\n"
          + "1. Goal clear karo: exactly kya banana ya solve karna hai.\n"
          + "2. Current state batao: abhi kya ready hai.\n"
          + "3. Error ya blocker paste karo agar hai.\n"
          + "4. Main tumhe exact steps, code idea, aur next action dunga.\n\n"
          + "Agar tum chaho to isi question ko detail me likho, main direct plan bana dunga.";
    }

    if (containsAny(message, "java", "spring", "springboot", "code", "api", "backend", "frontend")) {
      return "Haan bhai, tech question me bhi help kar sakta hoon.\n\n"
          + "Tumne poocha: \"" + message + "\"\n\n"
          + "Quick answer:\n"
          + "Agar ye coding concept hai, main explanation, example, aur implementation steps de sakta hoon. Agar ye project issue hai, mujhe file name, command, aur error bhejo.\n\n"
          + "Next message me ye format bhejo:\n"
          + "\"Goal: ..., File: ..., Error: ..., Expected: ...\"";
    }

    if (containsAny(message, "explain", "samjhao", "kya hai", "what is", "kaise", "how to")) {
      return "Haan bhai, explain kar deta hoon.\n\n"
          + "Tumhara question: \"" + message + "\"\n\n"
          + "Short answer:\n"
          + "Is topic ko samajhne ka best tareeka hai pehle definition, phir use case, phir example dekhna.\n\n"
          + "Better answer ke liye ek line aur bhejo: topic kis context me hai - business, coding, study, ya daily life? Phir main exact example ke saath explain karunga.";
    }

    return "Samjha bhai. Main help kar sakta hoon, bas question thoda specific kar do.\n\n"
        + "Tum ye batao:\n"
        + "1. Kis topic me help chahiye?\n"
        + "2. Problem exactly kya hai?\n"
        + "3. Tumhe short answer chahiye ya step-by-step explanation?\n\n"
        + "Note: ChatGPT jaisi full intelligence ke liye `OPENAI_API_KEY` set karna padega. Key set hote hi main har type ke question ka much smarter answer de paunga.";
  }

  private String detectConcern(String message) {
    if (containsAny(message, "customer", "lead", "client", "traffic", "sale", "conversion")) {
      return "getting customers and sales";
    }
    if (containsAny(message, "money", "fund", "investment", "capital", "cash", "loan")) {
      return "funding and cash flow";
    }
    if (containsAny(message, "price", "charge", "pricing", "margin", "profit")) {
      return "pricing and profit margin";
    }
    if (containsAny(message, "competitor", "competition", "different", "unique")) {
      return "competition and differentiation";
    }
    if (containsAny(message, "validate", "demand", "market", "will people buy", "idea work")) {
      return "idea validation";
    }
    if (containsAny(message, "team", "hire", "staff", "operation", "manage", "delivery")) {
      return "operations and execution";
    }
    if (containsAny(message, "marketing", "ads", "instagram", "seo", "promotion", "brand")) {
      return "marketing strategy";
    }
    return "business clarity and next steps";
  }

  private String answerForConcern(String concern, String businessType) {
    return switch (concern) {
      case "getting customers and sales" ->
          "Focus on one customer segment and one clear offer. For " + businessType
              + ", the fastest path is a simple landing page, proof of value, and daily outreach to qualified prospects.";
      case "funding and cash flow" ->
          "Start lean before raising money. Build a version that can get paying customers in 7-14 days, then use revenue and proof to ask for funding.";
      case "pricing and profit margin" ->
          "Do not price only by cost. Price by outcome, urgency, and customer value. Keep a starter offer for trust and a premium offer for serious customers.";
      case "competition and differentiation" ->
          "You do not need to beat every competitor. Win one narrow niche by being faster, clearer, easier to buy from, and better at follow-up.";
      case "idea validation" ->
          "Validate with real buyers before building too much. Talk to 20 target customers and try to collect 3 paid commitments or bookings.";
      case "operations and execution" ->
          "Turn the work into a repeatable process. Use checklists, templates, and automation so delivery quality does not depend on memory.";
      case "marketing strategy" ->
          "Pick one primary channel first. Create proof-based content, send direct outreach, and measure leads weekly before expanding channels.";
      default ->
          "Your next best move is to define the customer, problem, offer, price, and first sales channel. Clarity here will make every later decision easier.";
    };
  }

  private String explanationForConcern(String concern) {
    return switch (concern) {
      case "getting customers and sales" ->
          "Most early businesses fail because the offer is too broad. A focused customer and clear result make sales easier.";
      case "funding and cash flow" ->
          "Funding helps only after the model is clear. Early revenue proves demand and reduces risk.";
      case "pricing and profit margin" ->
          "Low pricing can create more work but less profit. Good pricing protects service quality and growth.";
      case "competition and differentiation" ->
          "Customers choose the option that feels trustworthy and easy, not always the cheapest or biggest.";
      case "idea validation" ->
          "Real demand is shown by calls, bookings, deposits, or purchases, not only positive comments.";
      case "operations and execution" ->
          "A business becomes scalable when the same result can be delivered repeatedly.";
      case "marketing strategy" ->
          "Consistent messaging on one channel beats random effort across five channels.";
      default ->
          "A strong business answer starts by reducing confusion into a practical next action.";
    };
  }

  private String actionPlanForConcern(String concern) {
    return switch (concern) {
      case "getting customers and sales" ->
          "1. Define one target customer.\n"
              + "2. Write one offer with price and result.\n"
              + "3. Make a demo or landing page.\n"
              + "4. Contact 30 prospects.\n"
              + "5. Track replies, calls, and conversions.";
      case "funding and cash flow" ->
          "1. List must-have launch costs.\n"
              + "2. Remove non-essential spending.\n"
              + "3. Create a paid pilot offer.\n"
              + "4. Collect advance payments if possible.\n"
              + "5. Reinvest only after proof.";
      case "pricing and profit margin" ->
          "1. Calculate cost and time.\n"
              + "2. Add minimum profit margin.\n"
              + "3. Create 3 packages: starter, growth, premium.\n"
              + "4. Test pricing with 5 prospects.\n"
              + "5. Increase price when demand is steady.";
      case "competition and differentiation" ->
          "1. List 5 competitors.\n"
              + "2. Compare offer, price, proof, speed, support.\n"
              + "3. Find one missing promise.\n"
              + "4. Build your offer around that gap.\n"
              + "5. Show proof publicly.";
      case "idea validation" ->
          "1. Write the problem in one sentence.\n"
              + "2. Interview 20 target customers.\n"
              + "3. Ask what they currently use.\n"
              + "4. Offer a paid pilot.\n"
              + "5. Build only after commitment.";
      case "operations and execution" ->
          "1. Break delivery into steps.\n"
              + "2. Create templates for repeated work.\n"
              + "3. Automate reminders and lead capture.\n"
              + "4. Track delivery time.\n"
              + "5. Improve the slowest step weekly.";
      case "marketing strategy" ->
          "1. Pick one customer segment.\n"
              + "2. Pick one channel.\n"
              + "3. Create 5 proof posts or messages.\n"
              + "4. Send daily outreach.\n"
              + "5. Review metrics every 7 days.";
      default ->
          "1. Define customer.\n"
              + "2. Define problem.\n"
              + "3. Define offer.\n"
              + "4. Define price.\n"
              + "5. Take one sales action today.";
    };
  }

  private String businessIdeaGenerator(LeadSnapshot lead) {
    String businessType = valueOrFallback(lead.getBusinessType(), "local service business");
    return "Business Idea Generator\n\n"
        + "1. AI-assisted " + businessType + " booking platform\n"
        + "   Problem: customers want fast discovery, pricing, and appointment booking.\n"
        + "   Offer: website + chatbot + WhatsApp follow-up.\n\n"
        + "2. Niche lead generation service for " + businessType + " owners\n"
        + "   Problem: owners struggle to bring qualified leads consistently.\n"
        + "   Offer: landing pages, ads, CRM pipeline, and monthly reporting.\n\n"
        + "3. Subscription automation kit for small businesses\n"
        + "   Problem: repeated customer questions waste staff time.\n"
        + "   Offer: support bot, FAQ automation, payment reminders, and analytics.\n\n"
        + "Next step: tell me your city, business type, and budget so I can shortlist the strongest idea.";
  }

  private String marketResearchAssistant(LeadSnapshot lead) {
    String businessType = valueOrFallback(lead.getBusinessType(), "your target business");
    return "Market Research Assistant\n\n"
        + "Target market: " + businessType + " customers who want convenience, speed, and trust.\n\n"
        + "Research checklist:\n"
        + "- Customer pain: slow response, unclear pricing, weak online presence.\n"
        + "- Demand signals: search volume, local competitors, social reviews, referral behavior.\n"
        + "- Buyer decision factors: price, trust, availability, proof of results.\n"
        + "- Channels to study: Google Maps, Instagram, Justdial, LinkedIn, local WhatsApp groups.\n\n"
        + "Questions to validate:\n"
        + "1. Who pays most often?\n"
        + "2. What problem happens weekly?\n"
        + "3. What price feels acceptable?\n"
        + "4. Which competitor already owns trust?";
  }

  private String competitorAnalysis(LeadSnapshot lead) {
    return "Competitor Analysis\n\n"
        + "Compare competitors on these 6 points:\n"
        + "1. Offer: what exactly they sell.\n"
        + "2. Pricing: visible plans, hidden quotes, discounts.\n"
        + "3. Proof: reviews, testimonials, case studies.\n"
        + "4. Speed: response time and booking flow.\n"
        + "5. Marketing: ads, social content, SEO pages.\n"
        + "6. Weakness: missing feature you can use as advantage.\n\n"
        + "Differentiation idea:\n"
        + "Build faster onboarding, clearer pricing, chatbot support, and a simple guarantee.\n\n"
        + "Send 2-3 competitor names and I will make a sharper comparison table.";
  }

  private String businessPlanGenerator(LeadSnapshot lead) {
    String service = valueOrFallback(lead.getService(), "business automation");
    return "Business Plan Generator\n\n"
        + "Executive summary:\n"
        + "Launch a " + service + " focused offer for small businesses that need more leads and faster customer response.\n\n"
        + "Offer:\n"
        + "- Website or landing page\n"
        + "- Chatbot for FAQs and lead capture\n"
        + "- Follow-up workflow through email or WhatsApp\n\n"
        + "Revenue model:\n"
        + "- Setup fee\n"
        + "- Monthly support plan\n"
        + "- Add-ons for ads, analytics, and CRM integration\n\n"
        + "Operations:\n"
        + "- Week 1: discovery and content\n"
        + "- Week 2: build and chatbot setup\n"
        + "- Week 3: launch, tracking, and optimization\n\n"
        + "Success metrics:\n"
        + "- Leads per week\n"
        + "- Conversion rate\n"
        + "- Cost per lead\n"
        + "- Response time";
  }

  private String salesMarketingStrategy(LeadSnapshot lead) {
    String businessType = valueOrFallback(lead.getBusinessType(), "small business");
    return "Sales & Marketing Strategy\n\n"
        + "Audience: owners of " + businessType + " who need predictable leads.\n\n"
        + "Positioning:\n"
        + "\"We help local businesses capture leads 24/7 with a website, chatbot, and follow-up system.\"\n\n"
        + "Channels:\n"
        + "- Google Maps outreach\n"
        + "- Instagram proof posts\n"
        + "- WhatsApp follow-up\n"
        + "- Referral offers\n"
        + "- Local SEO landing pages\n\n"
        + "7-day action plan:\n"
        + "Day 1: define niche and offer\n"
        + "Day 2: create demo page\n"
        + "Day 3: list 50 prospects\n"
        + "Day 4: send outreach\n"
        + "Day 5: follow up with proof\n"
        + "Day 6: book calls\n"
        + "Day 7: close first pilot";
  }

  private String startupCostCalculator(LeadSnapshot lead) {
    long suggestedBudget = lead.getBudget().isBlank() ? 25000 : Long.parseLong(lead.getBudget().replaceAll("\\D", ""));
    long setup = Math.max(8000, suggestedBudget / 3);
    long marketing = Math.max(7000, suggestedBudget / 3);
    long tools = Math.max(3000, suggestedBudget / 8);
    long buffer = Math.max(3000, suggestedBudget - setup - marketing - tools);
    long total = setup + marketing + tools + buffer;

    return "Startup Cost Calculator\n\n"
        + "Estimated launch budget: Rs. " + NumberFormat.getIntegerInstance(INDIA).format(total) + "\n\n"
        + "Breakdown:\n"
        + "- Setup and development: Rs. " + NumberFormat.getIntegerInstance(INDIA).format(setup) + "\n"
        + "- Marketing and outreach: Rs. " + NumberFormat.getIntegerInstance(INDIA).format(marketing) + "\n"
        + "- Tools and subscriptions: Rs. " + NumberFormat.getIntegerInstance(INDIA).format(tools) + "\n"
        + "- Buffer: Rs. " + NumberFormat.getIntegerInstance(INDIA).format(buffer) + "\n\n"
        + "Lean launch tip: start with one landing page, one chatbot flow, and one outreach channel.";
  }

  private String pitchDeckGenerator(LeadSnapshot lead) {
    return "Pitch Deck Content Generator\n\n"
        + "Slide 1: Vision\n"
        + "Make business growth simpler with AI-powered lead capture and automation.\n\n"
        + "Slide 2: Problem\n"
        + "Small businesses lose customers because responses are slow and online journeys are unclear.\n\n"
        + "Slide 3: Solution\n"
        + "A website + chatbot + follow-up engine that captures and qualifies leads 24/7.\n\n"
        + "Slide 4: Market\n"
        + "Local businesses, clinics, stores, restaurants, and service providers.\n\n"
        + "Slide 5: Product\n"
        + "Chatbot, landing page, analytics, CRM-ready lead pipeline.\n\n"
        + "Slide 6: Business model\n"
        + "Setup fee plus monthly support and optimization.\n\n"
        + "Slide 7: Go-to-market\n"
        + "Niche demos, local outreach, referrals, and SEO pages.\n\n"
        + "Slide 8: Ask\n"
        + "Pilot customers, strategic partners, or launch capital.";
  }

  private String customerPersonaBuilder(LeadSnapshot lead) {
    String businessType = valueOrFallback(lead.getBusinessType(), "local business");
    return "Customer Persona Builder\n\n"
        + "Persona: Growth-focused " + businessType + " owner\n\n"
        + "Profile:\n"
        + "- Age: 25-45\n"
        + "- Goal: more leads without hiring extra staff\n"
        + "- Pain: missed calls, slow replies, low online trust\n"
        + "- Budget behavior: pays when ROI is clear\n"
        + "- Buying trigger: losing customers to faster competitors\n\n"
        + "Message that works:\n"
        + "\"We help you respond instantly, capture leads, and show clear results every week.\"\n\n"
        + "Best channels:\n"
        + "- WhatsApp\n"
        + "- Google Maps\n"
        + "- Instagram\n"
        + "- Direct referral";
  }

  private String swotAnalysis(LeadSnapshot lead) {
    return "SWOT Analysis Generator\n\n"
        + "Strengths:\n"
        + "- Fast launch with website and chatbot\n"
        + "- Clear packages and measurable lead flow\n"
        + "- Low operating cost after setup\n\n"
        + "Weaknesses:\n"
        + "- Needs good content and niche clarity\n"
        + "- Early trust building takes effort\n"
        + "- Manual follow-up may still be needed\n\n"
        + "Opportunities:\n"
        + "- Local businesses are moving online\n"
        + "- AI support can reduce response time\n"
        + "- Recurring support plans create stable revenue\n\n"
        + "Threats:\n"
        + "- Cheap website competitors\n"
        + "- Low customer tech adoption\n"
        + "- Poor execution can reduce trust\n\n"
        + "Best move: pick one niche and build a strong demo with proof.";
  }

  private Plan recommendPlan(LeadSnapshot lead) {
    if ("Automation".equals(lead.getService())) {
      return plans.get(2);
    }
    if ("AI Chatbot".equals(lead.getService())) {
      return plans.get(1);
    }
    if (!lead.getBudget().isBlank()) {
      long amount = Long.parseLong(lead.getBudget().replaceAll("\\D", ""));
      if (amount >= 40000) {
        return plans.get(2);
      }
      if (amount >= 20000) {
        return plans.get(1);
      }
    }
    return plans.get(0);
  }

  private String leadSummary(LeadSnapshot lead) {
    return "Service: " + valueOrFallback(lead.getService(), "not selected yet") + "\n"
        + "Business: " + valueOrFallback(lead.getBusinessType(), "not shared yet") + "\n"
        + "Budget: " + valueOrFallback(lead.getBudget(), "not shared yet") + "\n"
        + "Contact: " + valueOrFallback(lead.getContact(), "not shared yet");
  }

  private boolean containsAny(String message, String... words) {
    for (String word : words) {
      if (message.contains(word)) {
        return true;
      }
    }
    return false;
  }

  private String valueOrFallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
