package com.buisnessbot.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OpenAiChatService {

  private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String model;

  public OpenAiChatService(
      ObjectMapper objectMapper,
      @Value("${openai.api.key:}") String apiKey,
      @Value("${openai.model:gpt-5.5}") String model) {
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .build();
    this.objectMapper = objectMapper;
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model == null || model.isBlank() ? "gpt-5.5" : model.trim();
  }

  public boolean isConfigured() {
    return !apiKey.isBlank();
  }

  public Optional<String> reply(String userMessage, LeadSnapshot lead, List<ChatTurn> history, String mode, String depth) {
    if (!isConfigured()) {
      return Optional.empty();
    }

    try {
      ObjectNode requestBody = buildRequest(userMessage, lead, history, mode, depth);
      HttpRequest request = HttpRequest.newBuilder(RESPONSES_URI)
          .timeout(Duration.ofSeconds(35))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return Optional.empty();
      }

      return extractText(response.body());
    } catch (IOException | InterruptedException error) {
      if (error instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return Optional.empty();
    }
  }

  private ObjectNode buildRequest(String userMessage, LeadSnapshot lead, List<ChatTurn> history, String mode, String depth) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("model", model);
    root.put("max_output_tokens", 900);

    ArrayNode input = root.putArray("input");
    addMessage(input, "developer", systemPrompt(lead, mode, depth));

    int start = Math.max(0, history.size() - 10);
    for (ChatTurn turn : history.subList(start, history.size())) {
      addMessage(input, turn.role(), turn.text());
    }
    addMessage(input, "user", userMessage);

    return root;
  }

  private void addMessage(ArrayNode input, String role, String text) {
    ObjectNode message = input.addObject();
    message.put("role", role);
    ArrayNode content = message.putArray("content");
    ObjectNode item = content.addObject();
    item.put("type", role.equals("assistant") ? "output_text" : "input_text");
    item.put("text", text);
  }

  private String systemPrompt(LeadSnapshot lead, String mode, String depth) {
    return """
        You are BUISNESS BOT, a universal AI assistant for any domain.
        Answer like a practical, intelligent chatbot. Be clear, specific, accurate, and useful.
        The user may write in Hindi, Hinglish, or English; reply in the same style when possible.
        You can help with business, coding, study, science, writing, career, daily life, creativity,
        productivity, planning, and general explanations.
        Avoid generic motivation. Give direct answers, reasoning, action steps, examples, and follow-up questions.
        If the user asks for business plans, market research, competitor analysis, SWOT, pitch deck,
        startup cost, customer persona, sales, marketing, pricing, funding, or operations, provide a structured report.
        For medical, legal, financial, or safety questions, give general educational guidance, explain uncertainty,
        and recommend consulting a qualified professional for decisions.
        Do not claim live/current facts unless the user provides them.
        Requested answer mode: %s.
        Requested answer depth: %s.
        If mode is advisor, focus on diagnosis and next action.
        If mode is plan, give a step-by-step execution plan.
        If mode is explain, teach the concept in simple words with an example.

        Current captured context:
        Business type: %s
        Service: %s
        Budget: %s
        Contact: %s
        """.formatted(
        valueOrFallback(mode, "advisor"),
        valueOrFallback(depth, "standard"),
        valueOrFallback(lead.getBusinessType(), "not shared"),
        valueOrFallback(lead.getService(), "not shared"),
        valueOrFallback(lead.getBudget(), "not shared"),
        valueOrFallback(lead.getContact(), "not shared"));
  }

  private Optional<String> extractText(String responseBody) throws IOException {
    JsonNode root = objectMapper.readTree(responseBody);
    JsonNode output = root.path("output");
    if (!output.isArray()) {
      return Optional.empty();
    }

    StringBuilder text = new StringBuilder();
    for (JsonNode item : output) {
      for (JsonNode content : item.path("content")) {
        JsonNode textNode = content.path("text");
        if (textNode.isTextual()) {
          if (!text.isEmpty()) {
            text.append("\n");
          }
          text.append(textNode.asText());
        }
      }
    }

    String answer = text.toString().trim();
    return answer.isBlank() ? Optional.empty() : Optional.of(answer);
  }

  private String valueOrFallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
