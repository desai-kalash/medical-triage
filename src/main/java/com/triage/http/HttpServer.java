package com.triage.http;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.util.Timeout;
import com.triage.http.ApiModels.*;
import com.triage.messages.Messages.*;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * PHASE 1: HttpServer - Connected to Actor System
 * Uses ProcessSymptoms message that TriageRouterActor actually handles
 */
public class HttpServer extends AllDirectives {
    
    private final ActorSystem<Void> system;
    private final ActorRef<TriageCommand> triageRouter;
    private final Timeout timeout = Timeout.create(Duration.ofSeconds(30));

    public HttpServer(ActorSystem<Void> system, ActorRef<TriageCommand> triageRouter) {
        this.system = system;
        this.triageRouter = triageRouter;
        System.out.println("🎭 PHASE 1: HttpServer connected to TriageRouterActor");
    }

    public Route createRoutes() {
        return concat(
            get(() -> pathSingleSlash(() -> 
                getFromResource("public/index.html")
            )),
            get(() -> pathPrefix("static", () -> 
                getFromResourceDirectory("public")
            )),
            path("chat", () ->
                post(() ->
                    entity(Jackson.unmarshaller(ChatRequest.class), request -> {
                        if (request.text == null || request.text.trim().isEmpty()) {
                            return complete(StatusCodes.BAD_REQUEST, "Missing text field");
                        }

                        // Quick non-medical filter for obvious cases
                        if (isObviouslyNonMedical(request.text.toLowerCase())) {
                            String sessionId = request.sessionId != null ? request.sessionId : generateSessionId();
                            ChatResponse response = createNonMedicalResponse(sessionId);
                            return completeOK(response, Jackson.marshaller());
                        }

                        // PHASE 1: Send to Actor System using ProcessSymptoms (what TriageRouter actually handles)
                        String sessionId = request.sessionId != null ? request.sessionId : generateSessionId();
                        
                        System.out.println("🎭 PHASE 1: Sending to TriageRouterActor: " + request.text);
                        
                        // FIXED: Use ProcessSymptoms message and correct AskPattern syntax
                        CompletionStage<TriageResponse> actorResponse = AskPattern.ask(
                            triageRouter,
                            replyTo -> new ProcessSymptoms(sessionId, request.text.trim(), replyTo),
                            Duration.ofSeconds(30),
                            system.scheduler()
                        );
                        
                        return onComplete(actorResponse, result -> {
    if (result.isSuccess()) {
        TriageResponse triageResponse = result.get();
        System.out.println("✅ PHASE 1: Got response from actors - Classification: " + triageResponse.classification);
        System.out.println("✅ PHASE 1: Recommendation length: " + triageResponse.recommendation.length() + " characters");
        
        ChatResponse chatResponse = convertTriageResponseToWeb(triageResponse);
        return completeOK(chatResponse, Jackson.marshaller());
    } else {
        // FIXED: Correct way to handle Akka Try failure
        try {
            result.get(); // This will throw the actual exception
        } catch (Exception throwable) {
            System.err.println("❌ PHASE 1: Actor system error: " + throwable.getMessage());
            throwable.printStackTrace();
        }
        
        ChatResponse errorResponse = createErrorResponse(sessionId);
        return complete(StatusCodes.INTERNAL_SERVER_ERROR, errorResponse, Jackson.marshaller());
    }
});
                    })
                )
            ),
            path("health", () ->
                get(() -> complete(StatusCodes.OK, "Phase 1: Actor Integration Active"))
            )
        );
    }

    // PHASE 1: Convert TriageResponse to web ChatResponse
    private ChatResponse convertTriageResponseToWeb(TriageResponse triageResponse) {
        System.out.println("🔄 PHASE 1: Converting TriageResponse to ChatResponse");
        
        // Map triage classification to web format
        boolean emergency = triageResponse.classification.equals("Emergency") || 
                           triageResponse.classification.equals("EMERGENCY") ||
                           triageResponse.severity.equals("Critical");
        
        // Create basic source list (will be enhanced in Phase 2 with real VDB sources)
        java.util.List<SourceRef> sources = java.util.List.of(
            new SourceRef("Medical Guidelines", "https://example.com/medical", 85.0)
        );
        
        String disclaimer = emergency ? 
            "🚨 This is an educational system. For real emergencies, call emergency services immediately!" :
            "This is an educational AI system. Always consult real medical professionals.";
        
        return new ChatResponse(
            triageResponse.sessionId,
            triageResponse.recommendation,
            triageResponse.classification,
            emergency,
            sources,
            disclaimer
        );
    }

    // Simple non-medical filter for Phase 1
    private boolean isObviouslyNonMedical(String textLower) {
        String[] obviousNonMedical = {
            "who is", "who was", "capital of", "president of",
            "movie", "song", "calculate", "weather today",
            "what is the capital", "biography of", "history of"
        };
        
        for (String pattern : obviousNonMedical) {
            if (textLower.contains(pattern)) {
                System.out.println("🔍 PHASE 1: Non-medical detected: " + pattern);
                return true;
            }
        }
        
        // Famous people
        if (textLower.contains("gandhi") || textLower.contains("einstein") || 
            textLower.contains("shakespeare") || textLower.contains("napoleon")) {
            System.out.println("🔍 PHASE 1: Famous person detected");
            return true;
        }
        
        return false;
    }

    private ChatResponse createNonMedicalResponse(String sessionId) {
        return new ChatResponse(
            sessionId,
            "⚠️ I'm a medical triage assistant designed to help with health symptoms and medical concerns.\n\n" +
            "Please describe your medical symptoms, such as:\n" +
            "• Pain, discomfort, or unusual sensations\n" +
            "• Changes in how you feel physically\n" +
            "• Health concerns or symptoms you're experiencing\n\n" +
            "Examples: 'I have chest pain', 'headache for 3 days', 'fever and cough'",
            "NonMedical",
            false,
            java.util.List.of(),
            "This system is designed for medical symptom assessment only."
        );
    }

    private ChatResponse createErrorResponse(String sessionId) {
        return new ChatResponse(
            sessionId,
            "I apologize, but I'm experiencing technical difficulties processing your request. " +
            "If this is a medical emergency, please call emergency services immediately (911).\n\n" +
            "Please try again in a moment, or contact a healthcare professional directly.",
            "Error",
            false,
            java.util.List.of(),
            "System temporarily unavailable. For emergencies, call emergency services."
        );
    }

    private String generateSessionId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    public CompletionStage<ServerBinding> start(String host, int port) {
        return Http.get(system).newServerAt(host, port).bind(createRoutes())
            .thenApply(binding -> {
                System.out.println("🌐 WEB SERVER STARTED: http://localhost:" + port);
                System.out.println("🎭 PHASE 1: Web interface connected to Actor System!");
                System.out.println("🧪 Test with: 'I have chest pain' to see actor communication");
                return binding;
            });
    }
}