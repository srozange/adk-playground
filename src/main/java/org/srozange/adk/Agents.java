package org.srozange.adk;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.Gemini;
import com.google.adk.models.Model;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.GoogleSearchTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Scanner;

import static java.lang.System.getenv;

public class Agents {
    private static final String NAME = "coordinator-agent";
    private static final String USER_ID = "test-user";

    // ROOT_AGENT needed for ADK Web UI.
    public static final BaseAgent ROOT_AGENT = initAgent();

    public static final boolean REMOTE = true;

    private static BaseAgent initAgent() {
        BaseLlm baseModel;
        if (REMOTE) {
            baseModel = Gemini.builder()
                    .modelName("gemini-2.0-flash")
                    .apiKey(getenv("GOOGLE_API_KEY"))
                    .build();
        } else {
            baseModel = new LangChain4j(OllamaChatModel.builder()
                    .modelName("qwen2.5:3b")
                    .baseUrl("http://localhost:11434")
                    .timeout(Duration.ofMinutes(2))
                    .build());
        }

        LlmAgent mathExpert = LlmAgent.builder()
                .name("math-expert")
                .description("A mathematics assistant.")
                .model(baseModel)
                .instruction("You are a mathematics expert. Always include in your answer that you are a mathematics expert.")
                .build();

        LlmAgent historyExpert = LlmAgent.builder()
                .name("history-expert")
                .description("A history assistant.")
                .model(baseModel)
                .instruction("You're a history expert. Always include in your answer that you are a history expert.")
                .build();

        return LlmAgent.builder()
                .name("coordinator-agent")
                .description("A general-purpose assistant.")
                .model(baseModel)
                .instruction("""
                            Your role is to assist users.
                            Do not solve math problems yourself: use the `math-expert` for any mathematical queries.
                            Do not answer history questions yourself: use the `history-expert` for historical information.
                            For all other topics, simply reply that you don't know.
                            Answer in the same language as the question.
                        """)
                .subAgents(historyExpert, mathExpert)
                .build();
    }

    public static void main(String[] args) {
        InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT);
        Session session =
                runner
                        .sessionService()
                        .createSession(NAME, USER_ID)
                        .blockingGet();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nExiting coordinator agent. Goodbye!");
        }));

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.print("\nYou > ");
                String userInput = scanner.nextLine();
                if ("quit".equalsIgnoreCase(userInput)) {
                    break;
                }
                Content userMsg = Content.fromParts(Part.fromText(userInput));
                Flowable<Event> events = runner.runAsync(session, userMsg, RunConfig.builder().build());
                System.out.print("\nAgent > ");
                events.blockingForEach(event -> {
                    System.out.println(event.stringifyContent());
                });
            }
        }
    }
}
