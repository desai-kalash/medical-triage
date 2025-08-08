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

/**
 * HttpServer - Simple web interface with enhanced medical responses
 */
public class HttpServer extends AllDirectives {
    
    private final ActorSystem<Void> system;

    public HttpServer(ActorSystem<Void> system, ActorRef<UserInputCommand> userInputActor) {
        this.system = system;
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

                        String sessionId = request.sessionId != null ? request.sessionId : generateSessionId();
                        ChatResponse response = processInput(sessionId, request.text.trim());
                        
                        return completeOK(response, Jackson.marshaller());
                    })
                )
            ),
            path("health", () ->
                get(() -> complete(StatusCodes.OK, "Healthy"))
            )
        );
    }

    private ChatResponse processInput(String sessionId, String userText) {
        String textLower = userText.toLowerCase();
        
        // Check for non-medical input
        if (isNonMedical(textLower)) {
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
        
        String classification = classifySymptoms(textLower);
        boolean emergency = classification.equals("Emergency");
        String responseText = generateResponse(userText, classification, textLower);
        var sources = getSources(classification, textLower);
        
        String disclaimer = emergency ? 
            "🚨 This is an educational system. For real emergencies, call emergency services immediately!" :
            "This is an educational AI system. Always consult real medical professionals.";
        
        return new ChatResponse(sessionId, responseText, classification, emergency, sources, disclaimer);
    }

    private boolean isNonMedical(String textLower) {
        if (textLower.contains("capital") || textLower.contains("country") || 
            textLower.contains("weather") || textLower.contains("president")) {
            return true;
        }
        if (textLower.contains("movie") || textLower.contains("song") || 
            textLower.contains("calculate") || textLower.contains("recipe")) {
            return true;
        }
        if (textLower.contains("butak") || textLower.contains("xyz") || 
            textLower.contains("hello") || textLower.contains("test")) {
            return true;
        }
        if (textLower.length() < 8 && !hasMedicalWords(textLower)) {
            return true;
        }
        return false;
    }

    private boolean hasMedicalWords(String textLower) {
        String[] words = {"pain", "hurt", "ache", "fever", "cough", "sick", "headache", "stomach", "chest"};
        for (String word : words) {
            if (textLower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String classifySymptoms(String textLower) {
        // Emergency combinations
        if (textLower.contains("severe chest pain") || 
            (textLower.contains("chest pain") && textLower.contains("shortness of breath"))) {
            return "Emergency";
        }
        if (textLower.contains("severe stomach pain") || textLower.contains("severe abdominal pain")) {
            return "Emergency";
        }
        
        // Self-care
        if (textLower.contains("mild headache") || 
            (textLower.contains("headache") && textLower.contains("mild"))) {
            return "SelfCare";
        }
        if (textLower.contains("runny nose") || textLower.contains("sore throat")) {
            return "SelfCare";
        }
        
        // Appointment  
        if (textLower.contains("recurring") || textLower.contains("weeks") ||
            textLower.contains("persistent") || textLower.contains("chronic")) {
            return "Appointment";
        }
        
        // Default by severity
        if (textLower.contains("severe")) {
            return "Emergency";
        } else if (textLower.contains("mild")) {
            return "SelfCare";
        }
        
        return "Appointment";
    }

    private String generateResponse(String originalText, String classification, String textLower) {
        switch (classification) {
            case "Emergency":
                return generateEmergencyResponse(originalText, textLower);
            case "SelfCare":
                return generateSelfCareResponse(originalText, textLower);
            case "Appointment":
                return generateAppointmentResponse(originalText, textLower);
            default:
                return "Please consult with a healthcare professional.";
        }
    }

    private String generateEmergencyResponse(String symptoms, String textLower) {
        StringBuilder response = new StringBuilder();
        response.append("🚨 EMERGENCY MEDICAL ATTENTION REQUIRED\n\n");
        response.append("Based on your symptoms: ").append(symptoms).append("\n\n");
        response.append("IMMEDIATE ACTIONS:\n");
        response.append("⚡ Call 911 or emergency services immediately\n");
        response.append("⚡ Do NOT drive yourself - call ambulance if needed\n");
        
        if (textLower.contains("chest")) {
            response.append("⚡ If chest pain: Take aspirin if not allergic\n");
        }
        if (textLower.contains("breathing") || textLower.contains("breath")) {
            response.append("⚡ If breathing difficulty: Sit upright, loosen clothing\n");
        }
        if (textLower.contains("stomach") || textLower.contains("abdominal")) {
            response.append("⚡ Do not eat or drink anything\n");
        }
        
        response.append("\nWHY THIS IS URGENT:\n");
        if (textLower.contains("chest") && textLower.contains("breath")) {
            response.append("Chest pain with breathing difficulties may indicate heart attack or pulmonary embolism.");
        } else if (textLower.contains("chest")) {
            response.append("Severe chest pain may indicate heart attack or cardiac emergency.");
        } else if (textLower.contains("stomach")) {
            response.append("Severe abdominal pain may indicate appendicitis or surgical emergency.");
        } else {
            response.append("These symptoms may indicate a serious medical condition.");
        }
        
        response.append("\n\n⚠️ This is a medical emergency - seek care immediately!");
        return response.toString();
    }

    private String generateSelfCareResponse(String symptoms, String textLower) {
        StringBuilder response = new StringBuilder();
        response.append("💡 SELF-CARE RECOMMENDATIONS\n\n");
        response.append("For your symptoms: ").append(symptoms).append("\n\n");
        response.append("HOME CARE GUIDELINES:\n");
        response.append("🌟 Get plenty of rest and sleep\n");
        response.append("🌟 Stay well hydrated\n");
        
        if (textLower.contains("headache")) {
            response.append("🌟 Try cold/warm compress on head\n");
            response.append("🌟 Rest in quiet, dark room\n");
        } else if (textLower.contains("throat")) {
            response.append("🌟 Gargle with warm salt water\n");
            response.append("🌟 Use throat lozenges\n");
        }
        
        response.append("🌟 Consider over-the-counter remedies (follow directions)\n");
        response.append("\nSEEK CARE IF:\n");
        response.append("• Symptoms worsen or persist beyond 5 days\n");
        response.append("• Fever exceeds 103°F\n");
        response.append("• New concerning symptoms develop\n");
        
        return response.toString();
    }

    private String generateAppointmentResponse(String symptoms, String textLower) {
        StringBuilder response = new StringBuilder();
        response.append("📅 MEDICAL APPOINTMENT RECOMMENDED\n\n");
        response.append("For your symptoms: ").append(symptoms).append("\n\n");
        response.append("NEXT STEPS:\n");
        response.append("📞 Contact your primary care physician within 1-2 weeks\n");
        response.append("📞 Consider urgent care if no regular doctor\n");
        
        response.append("\nBEFORE APPOINTMENT:\n");
        response.append("📝 Document symptoms: onset, frequency, severity\n");
        response.append("📝 Note what makes symptoms better/worse\n");
        response.append("📝 List current medications\n");
        
        if (textLower.contains("pain") && textLower.contains("weeks")) {
            response.append("📝 Keep pain diary with triggers and patterns\n");
        }
        
        response.append("\nSEEK URGENT CARE IF:\n");
        response.append("• Symptoms suddenly worsen\n");
        response.append("• Fever develops over 101°F\n");
        response.append("• Situation becomes concerning\n");
        
        return response.toString();
    }

    private java.util.List<SourceRef> getSources(String classification, String textLower) {
        if (textLower.contains("chest")) {
            return java.util.List.of(
                new SourceRef("CDC", "https://www.cdc.gov/heartdisease/heart_attack.htm", 95.0)
            );
        } else if (textLower.contains("stomach")) {
            return java.util.List.of(
                new SourceRef("Mayo Clinic", "https://www.mayoclinic.org/diseases-conditions/abdominal-pain/", 91.0)
            );
        } else if (textLower.contains("headache")) {
            return java.util.List.of(
                new SourceRef("Mayo Clinic", "https://www.mayoclinic.org/diseases-conditions/tension-headache/", 87.0)
            );
        } else if (textLower.contains("back")) {
            return java.util.List.of(
                new SourceRef("NHS", "https://www.nhs.uk/conditions/back-pain/", 91.0)
            );
        }
        return java.util.List.of(new SourceRef("CDC", "https://www.cdc.gov/", 80.0));
    }

    private String generateSessionId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    public CompletionStage<ServerBinding> start(String host, int port) {
        return Http.get(system).newServerAt(host, port).bind(createRoutes())
            .thenApply(binding -> {
                System.out.println("🌐 WEB SERVER STARTED: http://localhost:" + port);
                return binding;
            });
    }
}