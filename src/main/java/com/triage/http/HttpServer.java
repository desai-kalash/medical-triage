package com.triage.http;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import com.triage.http.ApiModels.*;
import com.triage.messages.Messages.*;

import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HttpServer - Simple web interface for Medical Triage Assistant
 * Serves static files and provides /chat API endpoint
 */
public class HttpServer extends AllDirectives {
    
    private final ActorSystem<Void> system;
    private final ActorRef<UICommand> uiOrchestrator;

    public HttpServer(ActorSystem<Void> system, ActorRef<UICommand> uiOrchestrator) {
        this.system = system;
        this.uiOrchestrator = uiOrchestrator;
    }

    public Route createRoutes() {
        return concat(
            // Serve main page
            get(() -> pathSingleSlash(() -> 
                getFromResource("public/index.html")
            )),

            // Serve static files (CSS, JS)
            get(() -> pathPrefix("static", () -> 
                getFromResourceDirectory("public")
            )),

            // API endpoint for chat - simplified approach
            path("chat", () ->
                post(() ->
                    entity(Jackson.unmarshaller(ChatRequest.class), request -> {
                        // Validate request
                        if (request.text == null || request.text.trim().isEmpty()) {
                            return complete(StatusCodes.BAD_REQUEST, 
                                "Missing or empty 'text' field");
                        }

                        // For now, return a simple mock response
                        // TODO: Integrate with UI orchestrator properly
                        String classification = classifySimple(request.text);
                        boolean emergency = classification.equals("Emergency");
                        
                        ChatResponse response = new ChatResponse(
                            request.sessionId != null ? request.sessionId : generateSessionId(),
                            generateMockResponse(request.text, classification),
                            classification,
                            emergency,
                            java.util.List.of(new SourceRef("CDC", "https://www.cdc.gov", 0.8)),
                            "This is an educational AI system. Always consult real medical professionals."
                        );

                        return completeOK(response, Jackson.marshaller());
                    })
                )
            ),

            // Health check endpoint
            path("health", () ->
                get(() -> complete(StatusCodes.OK, "Medical Triage Assistant - Healthy"))
            )
        );
    }

    private String generateSessionId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String classifySimple(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("chest pain") || lower.contains("severe") || 
            lower.contains("emergency") || lower.contains("breathless")) {
            return "Emergency";
        } else if (lower.contains("mild") || lower.contains("headache")) {
            return "SelfCare";
        } else {
            return "Appointment";
        }
    }

    private String generateMockResponse(String text, String classification) {
        switch (classification) {
            case "Emergency":
                return "🚨 EMERGENCY: Based on your symptoms (" + text + "), seek immediate medical attention. Call 911 or go to nearest ER.";
            case "SelfCare":
                return "💡 SELF-CARE: Your symptoms suggest a condition that may be manageable with home care. Try rest, hydration, and over-the-counter remedies.";
            default:
                return "📅 APPOINTMENT: I recommend scheduling an appointment with your healthcare provider to discuss these symptoms.";
        }
    }

    public CompletionStage<ServerBinding> start(String host, int port) {
        Http http = Http.get(system);
        
        Route routes = createRoutes();
        
        return http.newServerAt(host, port)
                   .bind(routes)
                   .thenApply(binding -> {
                       System.out.println("\n🌐 ============================================");
                       System.out.println("🌐 MEDICAL TRIAGE WEB INTERFACE STARTED");
                       System.out.println("🌐 ============================================");
                       System.out.println("🖥️  Server: http://localhost:" + port);
                       System.out.println("💬 Chat Interface: http://localhost:" + port);
                       System.out.println("🔍 Health Check: http://localhost:" + port + "/health");
                       System.out.println("📡 API Endpoint: POST http://localhost:" + port + "/chat");
                       System.out.println("🏥 Ready for web-based medical triage...");
                       System.out.println("============================================\n");
                       return binding;
                   });
    }
}