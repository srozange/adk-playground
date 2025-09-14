package org.srozange.adk;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.GoogleSearchTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Agents {

    private static final String NAME = "search_assistant";
    private static final String USER_ID = "test-user";

    // ROOT_AGENT needed for ADK Web UI.
    public static final BaseAgent ROOT_AGENT = initAgent();

    private static BaseAgent initAgent() {
        LlmAgent mathAgent = LlmAgent.builder()
                .name("math-agent")
                .description("A mathematics assistant.")
                .model("gemini-2.0-flash") // Or your preferred model
                .instruction("Always include in your answer that you are a math agent.")
                .afterAgentCallback(callbackContext -> {
                    callbackContext.eventActions()
                            .setTransferToAgent("generalist-agent");
                    return Maybe.empty();
                })
                .build();

        LlmAgent historyAgent = LlmAgent.builder()
                .name("history-agent")
                .description("A history assistant.")
                .model("gemini-2.0-flash") // Or your preferred model
                .instruction("Always include in your answer that you are a history agent.")
                .afterAgentCallback(callbackContext -> {
                    callbackContext.eventActions()
                            .setTransferToAgent("generalist-agent");
                    return Maybe.empty();
                })
                .build();

        return LlmAgent.builder()
                .name("generalist-agent")
                .description("A general-purpose assistant.")
                .model("gemini-2.0-flash") // Or your preferred model
                .instruction("""
                    Your role is to assist users.
                    Do not solve math problems yourself: use the `math-agent` for any mathematical queries.
                    Do not answer history questions yourself: use the `history-agent` for historical information.
                    For all other topics, simply reply that you don't know.
                    Answer in the same language than the question.                    
                """)
                .subAgents(historyAgent, mathAgent)
                //.tools(new GoogleSearchTool())
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
                    System.out.println("\nExiting generalist assistant. Goodbye!");
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
