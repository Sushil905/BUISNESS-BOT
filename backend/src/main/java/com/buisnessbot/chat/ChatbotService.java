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
  private final GeminiChatService geminiChatService;
  private final OpenAiChatService openAiChatService;
  private final List<Plan> plans = List.of(
      new Plan("Starter", "Rs. 9,999", "landing page + contact flow"),
      new Plan("Growth", "Rs. 24,999", "website + chatbot + analytics"),
      new Plan("Scale", "Rs. 49,999+", "custom automation + CRM integration"));

  public ChatbotService(GeminiChatService geminiChatService, OpenAiChatService openAiChatService) {
    this.geminiChatService = geminiChatService;
    this.openAiChatService = openAiChatService;
  }

  public ChatResponse reply(ChatRequest request) {
    LeadSnapshot lead = leads.computeIfAbsent(request.sessionId(), ignored -> new LeadSnapshot());
    List<ChatTurn> history = conversations.computeIfAbsent(request.sessionId(), ignored -> new ArrayList<>());
    updateLead(lead, request.message());

    String lower = request.message().toLowerCase(Locale.ROOT).trim();
    String mode = normalizeOption(request.responseMode(), "advisor", "advisor", "plan", "explain");
    String depth = normalizeOption(request.responseDepth(), "standard", "quick", "standard", "deep");
    List<ChatTurn> recentHistory = List.copyOf(history);
    String messageWithAttachments = appendAttachmentContext(request.message(), request.attachments());
    String reply = geminiChatService.reply(messageWithAttachments, lead, recentHistory, mode, depth)
        .or(() -> openAiChatService.reply(messageWithAttachments, lead, recentHistory, mode, depth))
        .orElseGet(() -> applyResponsePreferences(
            hasAttachments(request.attachments()) ? attachmentFallbackReply(request.attachments()) : buildReply(lower, lead),
            lower,
            mode,
            depth));

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

  private boolean hasAttachments(List<ChatAttachment> attachments) {
    return attachments != null && !attachments.isEmpty();
  }

  private String appendAttachmentContext(String message, List<ChatAttachment> attachments) {
    if (attachments == null || attachments.isEmpty()) {
      return message;
    }

    StringBuilder context = new StringBuilder(message).append("\n\nUploaded attachments:\n");
    int index = 1;
    for (ChatAttachment attachment : attachments) {
      context.append(index++).append(". ")
          .append(valueOrFallback(attachment.name(), "unnamed file"))
          .append(" | type: ").append(valueOrFallback(attachment.type(), "unknown"))
          .append(" | category: ").append(valueOrFallback(attachment.category(), "file"))
          .append(" | size: ").append(formatBytes(attachment.size()));

      if (attachment.textSnippet() != null && !attachment.textSnippet().isBlank()) {
        context.append("\n   Text snippet: ").append(attachment.textSnippet().trim());
      }
      context.append("\n");
    }

    context.append("""

        Note: The app has uploaded file metadata and any browser-readable text snippets.
        If the user asks for analysis, explain what can be inferred from this context and ask for more details when binary content is not available.
        """);
    return context.toString();
  }

  private String formatBytes(long size) {
    if (size <= 0) {
      return "unknown";
    }
    if (size < 1024) {
      return size + " B";
    }
    if (size < 1024 * 1024) {
      return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
    }
    return String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024.0));
  }

  private String attachmentFallbackReply(List<ChatAttachment> attachments) {
    StringBuilder reply = new StringBuilder("Files received.\n\n");
    reply.append("I can see these uploads:\n");
    int index = 1;
    for (ChatAttachment attachment : attachments) {
      reply.append(index++).append(". ")
          .append(valueOrFallback(attachment.name(), "unnamed file"))
          .append(" - ")
          .append(valueOrFallback(attachment.category(), "file"))
          .append(", ")
          .append(formatBytes(attachment.size()))
          .append("\n");
    }

    reply.append("""

        What I can do next:
        1. Make a summary or improvement plan.
        2. Create a presentation outline from these files.
        3. Suggest a professional design/layout.
        4. Turn screenshots/images into UI improvement notes.
        5. Prepare video review points or content ideas.

        For deep file reading, set GEMINI_API_KEY or OPENAI_API_KEY so the AI model can reason over the attachment context.
        """);
    return reply.toString();
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

    if (shouldUseAdaptiveQuestionReply(message)) {
      return generalAssistantReply(message);
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
    if (isQuestionLike(message)) {
      return false;
    }

    String clean = message.replaceAll("[^a-zA-Z0-9 ]", "").trim();
    return clean.length() < 12 && !containsAny(clean.toLowerCase(Locale.ROOT), "cost", "plan", "idea", "swot");
  }

  private boolean shouldUseAdaptiveQuestionReply(String message) {
    return isQuestionLike(message);
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
        + "Ab tum kisi bhi domain ka question pooch sakte ho: business, coding, study, science, career, writing, ya daily life.\n\n"
        + "Examples:\n"
        + "- Mere shop me customers nahi aa rahe\n"
        + "- What is photosynthesis?\n"
        + "- Java Spring Boot API kaise banate hain?\n"
        + "- Resume summary improve karo\n"
        + "- Explain inflation in simple words";
  }

  private String smallTalkReply() {
    return "Main badhiya hoon bhai, ready to help.\n\n"
        + "Tum apna question seedha type karo. Main direct answer, explanation, aur next steps dunga.\n\n"
        + "Example: \"What is API?\", \"How to improve sales?\", ya \"Explain gravity in simple words.\"";
  }

  private String helpReply() {
    return "Haan bhai, bilkul help karunga.\n\n"
        + "Bas apna question ya problem clearly likh do. Main simple language me answer, explanation, aur next steps dunga.\n\n"
        + "Tum kisi bhi domain me pooch sakte ho:\n"
        + "- Java Spring Boot kaise run karu?\n"
        + "- What is photosynthesis?\n"
        + "- Marketing strategy kaise banau?\n"
        + "- Resume ko professional kaise banau?\n"
        + "- Simple words me inflation samjhao";
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

    if (isQuestionLike(message)) {
      return adaptiveQuestionReply(message);
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

  private boolean isQuestionLike(String message) {
    return message.endsWith("?")
        || containsAny(message,
            "what is",
            "what are",
            "why",
            "how",
            "kaise",
            "kya hai",
            "kya hota",
            "samjhao",
            "explain",
            "best way",
            "which",
            "should i",
            "can i");
  }

  private String adaptiveQuestionReply(String message) {
    String topic = detectTopic(message);

    if (containsAny(message, "how", "kaise", "best way", "start", "create", "make", "build", "banau", "banao")) {
      return "Direct Answer\n\n"
          + stepAnswer(topic) + "\n\n"
          + "Professional approach:\n"
          + "1. Goal ko one-line me define karo.\n"
          + "2. Current situation note karo.\n"
          + "3. Smallest useful version banao.\n"
          + "4. Result measure karo.\n"
          + "5. Feedback ke hisaab se improve karo.\n\n"
          + "Next question jo mujhe chahiye:\n"
          + "Tumhara exact goal kya hai aur answer kis level par chahiye: beginner, practical, ya detailed?";
    }

    if (containsAny(message, "why", "kyu", "kyun")) {
      return "Direct Answer\n\n"
          + reasonAnswer(topic) + "\n\n"
          + "Simple explanation:\n"
          + "Agar reason clear hota hai to topic ko apply karna easy ho jata hai. Pehle cause samjho, phir example dekho, phir apne case me apply karo.\n\n"
          + "Next step:\n"
          + "Apna exact case bhejo, main reason ko tumhare situation ke according explain kar dunga.";
    }

    if (containsAny(message, "which", "best", "should i", "konsa", "kaunsa")) {
      return "Best Answer\n\n"
          + choiceAnswer(topic) + "\n\n"
          + "Decision rule:\n"
          + "1. Jo option fastest value de, usko prefer karo.\n"
          + "2. Jo option low cost me test ho sake, usse start karo.\n"
          + "3. Jo option long-term scalable ho, usko final plan banao.\n\n"
          + "Agar tum options bhej do, main compare karke best suggest kar dunga.";
    }

    return "Direct Answer\n\n"
        + explainTopic(topic) + "\n\n"
        + "Why it matters:\n"
        + importanceFor(topic) + "\n\n"
        + "Next steps:\n"
        + nextStepsFor(topic);
  }

  private String detectTopic(String message) {
    if (containsAny(message, "inflation")) {
      return "inflation";
    }
    if (containsAny(message, "chest pain", "heart pain", "can't breathe", "cant breathe", "breathing problem",
        "severe pain", "emergency")) {
      return "urgent health";
    }
    if (containsAny(message, "photosynthesis", "plant", "biology", "cell", "human body", "gravity", "force",
        "physics", "chemistry", "atom", "molecule", "science")) {
      return "science";
    }
    if (containsAny(message, "math", "algebra", "percentage", "calculate", "formula", "equation", "geometry")) {
      return "math";
    }
    if (containsAny(message, "history", "war", "empire", "civilization", "revolution")) {
      return "history";
    }
    if (containsAny(message, "inflation", "economy", "gdp", "tax", "stock", "investment", "loan", "finance")) {
      return "finance";
    }
    if (containsAny(message, "health", "medicine", "doctor", "symptom", "pain", "diet", "fitness", "exercise")) {
      return "health";
    }
    if (containsAny(message, "law", "legal", "contract", "rights", "case", "agreement")) {
      return "legal";
    }
    if (containsAny(message, "resume", "cv", "interview", "job", "career", "linkedin")) {
      return "career";
    }
    if (containsAny(message, "write", "content", "essay", "email", "caption", "script", "story", "blog")) {
      return "writing";
    }
    if (containsAny(message, "time management", "productivity", "habit", "routine", "study plan")) {
      return "productivity";
    }
    if (containsAny(message, "marketing", "sales", "lead", "customer")) {
      return "sales and marketing";
    }
    if (containsAny(message, "business plan", "startup plan")) {
      return "business plan";
    }
    if (containsAny(message, "swot")) {
      return "SWOT analysis";
    }
    if (containsAny(message, "competitor", "competition")) {
      return "competitor analysis";
    }
    if (containsAny(message, "chatbot", "bot")) {
      return "chatbot";
    }
    if (containsAny(message, "website", "landing page")) {
      return "website";
    }
    if (containsAny(message, "spring boot", "springboot")) {
      return "Spring Boot";
    }
    if (containsAny(message, "api")) {
      return "API";
    }
    if (containsAny(message, "java")) {
      return "Java";
    }
    if (containsAny(message, "startup", "business")) {
      return "business";
    }
    return "your question";
  }

  private String explainTopic(String topic) {
    return switch (topic) {
      case "sales and marketing" ->
          "Sales and marketing ka matlab hai right customer tak right message pahunchana, trust banana, aur unhe buyer me convert karna.";
      case "business plan" ->
          "Business plan ek roadmap hota hai jisme problem, customer, solution, pricing, marketing, operations, cost, aur growth plan clear hota hai.";
      case "SWOT analysis" ->
          "SWOT analysis business ko 4 angles se dekhne ka tool hai: strengths, weaknesses, opportunities, aur threats.";
      case "competitor analysis" ->
          "Competitor analysis me tum compare karte ho ki market me dusre players kya offer kar rahe hain, unki strength kya hai, aur tum kis gap ko win kar sakte ho.";
      case "chatbot" ->
          "Chatbot ek software assistant hota hai jo user ke messages ka jawab deta hai, leads capture karta hai, FAQs handle karta hai, aur support fast banata hai.";
      case "website" ->
          "Website business ka online front desk hai. Ye customer ko trust, information, pricing, contact, booking, aur proof dikhata hai.";
      case "Spring Boot" ->
          "Spring Boot Java framework hai jo backend APIs, web apps, configuration, and server setup ko fast aur organized banata hai.";
      case "API" ->
          "API ek bridge hota hai jisse frontend aur backend data exchange karte hain. Tumhara frontend `/api/chat` par message bhejta hai aur backend reply return karta hai.";
      case "Java" ->
          "Java ek programming language hai jo backend, Android, enterprise apps, APIs, aur large software systems banane ke kaam aati hai.";
      case "business" ->
          "Business ka core simple hai: ek customer problem solve karo, uske badle value charge karo, aur process repeatable banao.";
      case "science" ->
          scienceExplanation();
      case "math" ->
          "Math problems ko solve karne ka best tareeka hai: given values identify karo, formula choose karo, step-by-step calculate karo, aur final answer verify karo.";
      case "history" ->
          "History kisi event ko time, place, people, causes, effects, aur long-term impact ke through samajhne ka subject hai.";
      case "finance" ->
          "Finance money ko plan, manage, invest, borrow, save, aur grow karne ka discipline hai. Decisions me risk, return, time, and cash flow important hote hain.";
      case "inflation" ->
          "Inflation ka matlab hai prices ka time ke saath badhna. Jab inflation hota hai, same paise se pehle jitna samaan kharid paate the, utna nahi kharid paate.";
      case "urgent health" ->
          "Chest pain ya breathing problem serious ho sakti hai. Agar pain strong hai, saans me dikkat hai, sweating/chakkar/nausea hai, ya pain left arm/jaw/back me ja raha hai, emergency help lo.";
      case "health" ->
          "Health questions me safest approach hai symptoms, duration, lifestyle, and risk factors samajhna. Main general education de sakta hoon, diagnosis ke liye doctor best hai.";
      case "legal" ->
          "Legal questions me rules location aur exact facts par depend karte hain. Main general explanation de sakta hoon, final advice ke liye qualified lawyer se consult karna best hai.";
      case "career" ->
          "Career growth ka core hai: clear target role, strong resume, relevant skills, proof projects, interview practice, aur consistent applications.";
      case "writing" ->
          "Good writing clear purpose, right audience, simple structure, strong opening, useful details, aur clean ending se banti hai.";
      case "productivity" ->
          "Productivity ka matlab zyada busy rehna nahi, balki important kaam ko clear priority, focus blocks, and review system ke saath complete karna hai.";
      default ->
          "Tumhara question valid hai. Isko samajhne ke liye main definition, simple explanation, example, aur next step ke format me answer de sakta hoon.";
    };
  }

  private String stepAnswer(String topic) {
    return switch (topic) {
      case "sales and marketing" ->
          "Sales aur marketing improve karne ke liye pehle one target customer choose karo, one clear offer banao, proof collect karo, aur daily outreach + follow-up system set karo.";
      case "business plan" ->
          "Business plan banane ke liye problem, customer, solution, pricing, marketing channel, cost, revenue model, and 30-day execution plan likho.";
      case "SWOT analysis" ->
          "SWOT banane ke liye 4 boxes banao: strengths, weaknesses, opportunities, threats. Har box me 3-5 practical points likho, phir top 3 actions choose karo.";
      case "competitor analysis" ->
          "Competitor analysis ke liye 5 competitors list karo aur offer, pricing, proof, reviews, speed, marketing, aur weak points compare karo.";
      case "chatbot" ->
          "Chatbot banane ke liye intent list karo, conversation flow design karo, backend API connect karo, fallback answer add karo, aur logs se improve karo.";
      case "website" ->
          "Website banane ke liye sections clear rakho: hero, services, proof, pricing/contact, FAQ, and lead form. Mobile layout pehle test karo.";
      case "Spring Boot" ->
          "Spring Boot app banane ke liye controller, service, model, properties, and static frontend setup karo. API ko curl/Postman se test karo.";
      case "API" ->
          "API banane ke liye endpoint define karo, request/response model banao, service logic add karo, error handling rakho, aur frontend se fetch call connect karo.";
      case "Java" ->
          "Java seekhne ke liye syntax, OOP, collections, exceptions, file/API handling, and Spring Boot basics step-by-step practice karo.";
      case "business" ->
          "Business start karne ke liye one problem choose karo, 20 customers se baat karo, small paid offer test karo, then marketing and operations system build karo.";
      case "science" ->
          "Science topic samajhne ke liye concept define karo, process ke steps dekho, diagram/example banao, aur real-life application connect karo.";
      case "math" ->
          "Math solve karne ke liye question ko parts me todho: given data, required answer, formula, calculation, final check.";
      case "history" ->
          "History answer banane ke liye timeline banao, causes list karo, key people identify karo, event explain karo, aur impact likho.";
      case "finance" ->
          "Finance decision ke liye goal, time horizon, risk, budget, emergency fund, and expected return compare karo.";
      case "inflation" ->
          "Inflation samajhne ke liye price increase, income growth, savings value, and purchasing power compare karo.";
      case "urgent health" ->
          "Agar chest pain severe ya unusual hai, immediate medical help lo. Main diagnosis nahi kar sakta.";
      case "health" ->
          "Health improvement ke liye sleep, diet, exercise, hydration, stress, and symptoms tracking se start karo. Serious symptoms me doctor ko consult karo.";
      case "legal" ->
          "Legal issue handle karne ke liye facts, dates, documents, parties, location, and desired outcome list karo, phir lawyer se verify karo.";
      case "career" ->
          "Career improve karne ke liye target role choose karo, resume tailor karo, 2-3 proof projects banao, daily applications bhejo, aur interviews practice karo.";
      case "writing" ->
          "Writing ke liye pehle audience aur purpose define karo, outline banao, draft likho, edit karo, phir final polish karo.";
      case "productivity" ->
          "Productivity ke liye daily top 3 tasks choose karo, 45-60 minute focus blocks rakho, distractions remove karo, aur evening review karo.";
      default ->
          "Is question ko solve karne ke liye pehle objective clear karo, phir required steps list karo, smallest test run karo, aur result ke basis par improve karo.";
    };
  }

  private String reasonAnswer(String topic) {
    return switch (topic) {
      case "sales and marketing" ->
          "Sales aur marketing important hain kyunki good product bhi tab tak grow nahi hota jab tak right customer ko clear value samajh nahi aati.";
      case "business plan" ->
          "Business plan important hai kyunki ye random work ko focused execution me convert karta hai.";
      case "SWOT analysis" ->
          "SWOT useful hai kyunki ye business ke internal issues aur external market risks dono ko ek jagah visible banata hai.";
      case "competitor analysis" ->
          "Competitor analysis zaruri hai kyunki market me already kya kaam kar raha hai aur kya missing hai, dono samajh aata hai.";
      case "chatbot" ->
          "Chatbot useful hai kyunki customer ko instant response milta hai aur business missed leads kam karta hai.";
      case "website" ->
          "Website important hai kyunki customer pehle online trust check karta hai, phir contact ya purchase karta hai.";
      case "Spring Boot", "API", "Java" ->
          topic + " useful hai kyunki ye reliable, maintainable, and scalable software build karne me help karta hai.";
      case "science" ->
          "Science important hai kyunki ye natural world ko evidence aur experiments ke through explain karta hai.";
      case "math" ->
          "Math important hai kyunki ye problem solving, logic, finance, engineering, coding, aur daily calculations me kaam aata hai.";
      case "history" ->
          "History important hai kyunki past ke causes aur outcomes samajhkar better decisions liye ja sakte hain.";
      case "finance" ->
          "Finance important hai kyunki wrong money decisions long-term stress create kar sakte hain.";
      case "inflation" ->
          "Inflation important hai kyunki ye savings, salary, prices, loans, and purchasing power ko directly affect karta hai.";
      case "urgent health" ->
          "Chest pain ko lightly nahi lena chahiye kyunki kabhi-kabhi ye heart, lungs, or other serious causes se related ho sakta hai.";
      case "health" ->
          "Health important hai kyunki body aur mind ki condition directly energy, focus, and quality of life affect karti hai.";
      case "legal" ->
          "Legal clarity important hai kyunki small mistakes contracts, rights, money, ya compliance risk create kar sakti hain.";
      case "career" ->
          "Career clarity important hai kyunki random applications ke bajay targeted preparation faster results deti hai.";
      case "writing" ->
          "Writing important hai kyunki clear communication trust, marks, sales, interviews, and teamwork improve karta hai.";
      case "productivity" ->
          "Productivity important hai kyunki limited time me meaningful work complete karna growth ka base hai.";
      default ->
          "Ye important hai kyunki clear understanding ke bina decision guesswork ban jata hai.";
    };
  }

  private String choiceAnswer(String topic) {
    return switch (topic) {
      case "sales and marketing" ->
          "Best start usually one channel se hota hai: local business ke liye Google Maps/WhatsApp, online brand ke liye content + landing page, B2B ke liye LinkedIn/outreach.";
      case "chatbot" ->
          "Best chatbot approach hai: pehle rule-based core flows, phir OpenAI-powered answers, phir analytics-based improvement.";
      case "website" ->
          "Best website approach hai simple responsive landing page with clear offer, proof, contact form, and chatbot.";
      case "Spring Boot", "API", "Java" ->
          "Best option depend karta hai project goal par. Backend/API project ke liye Java + Spring Boot strong choice hai.";
      case "business" ->
          "Best business idea wahi hai jisme real customer pain, paying capacity, simple launch path, and repeat demand ho.";
      case "science", "math", "history" ->
          "Best learning method usually example-first hota hai: simple definition, one solved example, then practice question.";
      case "finance" ->
          "Best finance choice tumhare goal, risk tolerance, emergency fund, debt, and time horizon par depend karti hai.";
      case "inflation" ->
          "Best response to inflation is usually budget review, emergency fund, income growth, and sensible long-term investing after understanding risk.";
      case "urgent health" ->
          "Best action for concerning chest pain is urgent medical evaluation, not waiting for online advice.";
      case "health" ->
          "Best health choice symptoms aur personal condition par depend karti hai; general habits helpful hain, medical decision doctor se verify karo.";
      case "legal" ->
          "Best legal option facts, documents, and local law par depend karta hai; lawyer review safest rahega.";
      case "career" ->
          "Best career move wahi hai jo target role ke skills, proof, network, and applications ko directly improve kare.";
      case "writing" ->
          "Best writing style audience par depend karti hai: professional ke liye clear and concise, creative ke liye vivid and emotional.";
      case "productivity" ->
          "Best productivity system simple hota hai: top 3 tasks, calendar blocks, distraction control, and weekly review.";
      default ->
          "Best option choose karne ke liye speed, cost, risk, user value, and scalability compare karo.";
    };
  }

  private String importanceFor(String topic) {
    return switch (topic) {
      case "urgent health" ->
          "This can be time-sensitive. Online guidance cannot rule out serious medical causes.";
      case "health" ->
          "Health information useful hai, but personal diagnosis ke liye qualified doctor best hai.";
      case "legal" ->
          "Legal clarity useful hai, but exact advice jurisdiction aur documents par depend karti hai.";
      case "finance" ->
          "Finance concepts useful hain, but investment decisions me risk hota hai; personal advisor se verify karna smart hai.";
      default ->
          "Is concept ko samajhne se tum better decisions le paoge aur apne real situation me apply kar paoge.";
    };
  }

  private String nextStepsFor(String topic) {
    return switch (topic) {
      case "urgent health" ->
          "1. If symptoms are severe, call local emergency services now.\n"
              + "2. Do not drive yourself if you feel faint or breathless.\n"
              + "3. Share age, symptoms, duration, and medical history with a doctor.";
      case "health" ->
          "1. Note symptoms, duration, severity, and triggers.\n"
              + "2. Avoid self-diagnosis for serious symptoms.\n"
              + "3. Consult a qualified doctor for personal advice.";
      case "legal" ->
          "1. Collect dates, documents, parties, and exact facts.\n"
              + "2. Identify your location/jurisdiction.\n"
              + "3. Consult a qualified lawyer before taking action.";
      case "finance", "inflation" ->
          "1. Define your goal and risk level.\n"
              + "2. Review budget, savings, debt, and time horizon.\n"
              + "3. Verify investment or loan decisions with a qualified advisor.";
      default ->
          "1. Isko ek real example par apply karo.\n"
              + "2. Jo doubt aaye woh poochho.\n"
              + "3. Agar detailed answer chahiye to context, level, aur expected output batao.";
    };
  }

  private String scienceExplanation() {
    return "Science kisi natural process ko observation, evidence, experiment, and explanation ke through samajhne ka method hai. "
        + "Agar tum photosynthesis pooch rahe ho, simple answer: plants sunlight, water, and carbon dioxide ka use karke food banate hain aur oxygen release karte hain.";
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

  private String applyResponsePreferences(String reply, String message, String mode, String depth) {
    String focusedReply = switch (mode) {
      case "plan" -> "Execution Plan Mode\n\n" + reply + "\n\n30-minute next action:\n" + nextActionFor(message);
      case "explain" -> "Simple Explanation Mode\n\n" + reply + "\n\nExample:\n" + exampleFor(message);
      default -> reply;
    };

    if ("quick".equals(depth)) {
      return makeQuick(focusedReply);
    }

    if ("deep".equals(depth)) {
      return focusedReply + "\n\nAdvanced checklist:\n"
          + "1. Define success metric before acting.\n"
          + "2. Write assumptions clearly.\n"
          + "3. Test the smallest version first.\n"
          + "4. Track result for 7 days.\n"
          + "5. Improve based on real feedback.";
    }

    return focusedReply;
  }

  private String makeQuick(String reply) {
    String[] lines = reply.split("\\R");
    StringBuilder quick = new StringBuilder("Quick Answer\n\n");
    int added = 0;
    for (String line : lines) {
      String clean = line.trim();
      if (!clean.isBlank()) {
        quick.append(clean).append("\n");
        added++;
      }
      if (added >= 8) {
        break;
      }
    }

    String answer = quick.toString().trim();
    while (answer.endsWith(":")) {
      int lastBreak = answer.lastIndexOf('\n');
      if (lastBreak < 0) {
        break;
      }
      answer = answer.substring(0, lastBreak).trim();
    }
    return answer;
  }

  private String nextActionFor(String message) {
    if (containsAny(message, "chest pain", "heart pain", "can't breathe", "cant breathe", "breathing")) {
      return "If symptoms are severe or unusual, contact emergency medical help now.";
    }
    if (containsAny(message, "resume", "cv", "career", "job", "interview")) {
      return "Pick one target role and rewrite your resume summary for that role.";
    }
    if (containsAny(message, "inflation", "finance", "money", "investment")) {
      return "Write your monthly budget and identify which costs increased most.";
    }
    if (containsAny(message, "photosynthesis", "science", "biology", "physics", "chemistry")) {
      return "Write the concept in one sentence, then draw or imagine one simple example.";
    }
    if (containsAny(message, "customer", "lead", "sales", "marketing")) {
      return "List 20 target customers and send one clear offer message today.";
    }
    if (containsAny(message, "business", "startup", "idea")) {
      return "Write the problem, target customer, price, and first offer in one page.";
    }
    if (containsAny(message, "java", "spring", "api", "code")) {
      return "Create one small working example, run it, then improve only one part.";
    }
    return "Write your goal in one sentence and choose the first smallest action.";
  }

  private String exampleFor(String message) {
    if (containsAny(message, "photosynthesis")) {
      return "A plant leaf takes sunlight, water from roots, and carbon dioxide from air to make food. Oxygen comes out as a byproduct.";
    }
    if (containsAny(message, "inflation")) {
      return "If milk was Rs. 50 last year and Rs. 60 this year, prices increased. Your Rs. 50 now buys less than before.";
    }
    if (containsAny(message, "resume", "cv")) {
      return "Instead of \"Hardworking student\", write \"Java Spring Boot developer with 2 projects, REST API experience, and basic frontend skills.\"";
    }
    if (containsAny(message, "health", "chest pain", "heart pain")) {
      return "Mild acidity and serious heart-related pain can feel confusingly similar, so unusual or severe chest pain needs medical evaluation.";
    }
    if (containsAny(message, "legal", "law", "contract")) {
      return "A contract question depends on exact wording, location, dates, parties, and signatures.";
    }
    if (containsAny(message, "api")) {
      return "Frontend sends `{ message: \"hi\" }` to `/api/chat`; backend returns `{ reply: \"Hello\" }`.";
    }
    if (containsAny(message, "swot")) {
      return "For a shop: strength = loyal customers, weakness = weak online presence, opportunity = local delivery, threat = bigger competitors.";
    }
    if (containsAny(message, "marketing", "sales")) {
      return "If a clinic needs leads, it can use Google Maps reviews, a booking page, and WhatsApp follow-up.";
    }
    return "Take one real case from your project, apply the concept, and check what result changes.";
  }

  private String normalizeOption(String value, String fallback, String... allowedValues) {
    if (value == null || value.isBlank()) {
      return fallback;
    }

    String clean = value.toLowerCase(Locale.ROOT).trim();
    for (String allowed : allowedValues) {
      if (allowed.equals(clean)) {
        return clean;
      }
    }
    return fallback;
  }

  private String valueOrFallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
