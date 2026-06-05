package com.buisnessbot.chat;

import java.text.NumberFormat;
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
  private final List<Plan> plans = List.of(
      new Plan("Starter", "Rs. 9,999", "landing page + contact flow"),
      new Plan("Growth", "Rs. 24,999", "website + chatbot + analytics"),
      new Plan("Scale", "Rs. 49,999+", "custom automation + CRM integration"));

  public ChatResponse reply(ChatRequest request) {
    LeadSnapshot lead = leads.computeIfAbsent(request.sessionId(), ignored -> new LeadSnapshot());
    updateLead(lead, request.message());

    String lower = request.message().toLowerCase(Locale.ROOT).trim();
    String reply = buildReply(lower, lead);
    return new ChatResponse(reply, lead);
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

    return "Samjha. Thoda aur detail do: business type kya hai, kaunsi service chahiye, aur approx budget kya hai?\n\n"
        + "Aap ye bhi try kar sakte ho: business idea generator, market research, competitor analysis, "
        + "business plan, startup cost calculator, pitch deck, persona builder, ya SWOT analysis.";
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
