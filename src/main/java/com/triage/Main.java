package com.triage;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.actors.*;
import com.triage.messages.Messages.*;
import com.triage.http.HttpServer;

import java.util.Scanner;

/**
 * Medical Triage Assistant - Main with Web Interface
 * Starts both console and web interfaces
 * UPDATED: Now supports both single-node and cluster modes
 */
public class Main {
    
    private static ActorRef<UserInputCommand> userInputActor;
    private static ActorRef<LogCommand> logger;
    private static boolean systemReady = false;
    
    public static void main(String[] args) {
        System.out.println("🏥 Initializing Medical Triage Assistant with Web Interface...");
        
        ActorSystem<Void> system = ActorSystem.create(
            createWebEnabledBehavior(),
            "MedicalTriageSystem"
        );

        // Wait for system initialization
        waitForSystemReady();
        
        // Start interactive chatbot (console) - web runs in parallel
        startInteractiveChatbot(system);
    }
    
    private static void waitForSystemReady() {
        System.out.println("⏳ Starting up all actors and web server...");
        try {
            while (!systemReady) {
                Thread.sleep(500);
            }
            Thread.sleep(3000); // Wait for web server to start
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void startInteractiveChatbot(ActorSystem<Void> system) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\n🏥 ============================================");
        System.out.println("🏥 MEDICAL TRIAGE ASSISTANT - DUAL MODE");
        System.out.println("🏥 ============================================");
        System.out.println("💻 Console Interface: Ready for input below");
        System.out.println("🌐 Web Interface: http://localhost:8080");
        System.out.println("💬 Commands: 'quit' to exit, 'help' for guidance, 'web' for web info\n");

        showWelcomeMessage();
        
        while (true) {
            System.out.print("\n🩺 Console: ");
            String userInput = scanner.nextLine().trim();
            
            // Handle special commands
            if (userInput.equalsIgnoreCase("quit") || userInput.equalsIgnoreCase("exit")) {
                System.out.println("\n👋 Thank you for using Medical Triage Assistant!");
                System.out.println("🌐 Web interface will remain available at http://localhost:8080");
                break;
            }
            
            if (userInput.equalsIgnoreCase("web")) {
                System.out.println("\n🌐 WEB INTERFACE INFORMATION:");
                System.out.println("   URL: http://localhost:8080");
                System.out.println("   Features: Professional chat UI, emergency alerts, source links");
                System.out.println("   Mobile-friendly design with quick action buttons");
                continue;
            }
            
            if (userInput.equalsIgnoreCase("help")) {
                showHelp();
                continue;
            }
            
            if (userInput.equalsIgnoreCase("demo")) {
                System.out.println("\n🧪 Running automated demo tests...");
                runAutomatedDemo();
                System.out.println("\n💬 Demo completed! Try the web interface at http://localhost:8080");
                continue;
            }
            
            if (userInput.isEmpty()) {
                System.out.println("💡 Please describe your symptoms, 'web' for web info, or 'help' for guidance.");
                continue;
            }
            
            // Process real user input
            processUserSymptoms(userInput);
        }
        
        scanner.close();
        System.out.println("🔄 Shutting down system...");
        system.terminate();
    }
    
    private static void processUserSymptoms(String symptoms) {
        System.out.println("\n🔄 Analyzing your symptoms...");
        System.out.println("📡 Processing through actor system...");
        
        // Send user input to the triage system
        userInputActor.tell(new UserSymptomInput(symptoms));
        
        // Give time for processing
        try {
            Thread.sleep(4000); // Wait for analysis to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("─".repeat(60));
        System.out.println("🌐 Also try the web interface: http://localhost:8080");
    }
    
    private static void runAutomatedDemo() {
        String[] testCases = {
            "I have severe crushing chest pain that started 10 minutes ago, radiating down my left arm. I'm sweating and feel nauseous.",
            "I have a mild headache and runny nose that started this morning. No fever.",
            "I've been having recurring headaches for the past 2 weeks, usually in the afternoon.",
            "Sudden severe shortness of breath, chest pain, and dizziness. Started after long flight.",
            "I feel tired and have been coughing occasionally for a few days."
        };
        
        String[] scenarios = {
            "EMERGENCY SCENARIO",
            "SELF-CARE SCENARIO", 
            "APPOINTMENT SCENARIO",
            "COMPLEX EMERGENCY",
            "AMBIGUOUS SYMPTOMS"
        };
        
        for (int i = 0; i < testCases.length; i++) {
            System.out.println("\n🧪 TEST CASE " + (i + 1) + ": " + scenarios[i]);
            System.out.println("🩺 Symptoms: " + testCases[i]);
            
            logger.tell(new LogEvent("DEMO", "TestSuite", 
                "🧪 TEST CASE " + (i + 1) + ": " + scenarios[i], "INFO"));
            
            userInputActor.tell(new UserSymptomInput(testCases[i]));
            
            try {
                Thread.sleep(5000); // Wait between tests
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private static void showWelcomeMessage() {
        System.out.println("🎯 SYSTEM CAPABILITIES:");
        System.out.println("   • Real-time AI symptom analysis using Gemini");
        System.out.println("   • Vector database with medical knowledge retrieval");
        System.out.println("   • Distributed actor processing (tell/ask/forward patterns)");
        System.out.println("   • Intelligent triage routing (Emergency/Self-Care/Appointment)");
        System.out.println("   • Session tracking and conversation history");
        System.out.println("   • Web interface at http://localhost:8080");
    }
    
    private static void showHelp() {
        System.out.println("\n📋 HOW TO USE THE MEDICAL TRIAGE ASSISTANT:");
        System.out.println("─".repeat(60));
        System.out.println("🖥️  CONSOLE MODE (here): Type symptoms and get responses");
        System.out.println("🌐 WEB MODE: Open http://localhost:8080 for modern chat interface");
        System.out.println();
        System.out.println("🔍 GOOD EXAMPLES:");
        System.out.println("   • \"I have severe chest pain and shortness of breath\"");
        System.out.println("   • \"Mild headache and runny nose for 2 days, no fever\"");
        System.out.println("   • \"Sharp stomach pain that started after eating\"");
        System.out.println("   • \"Persistent cough for a week with yellow phlegm\"");
        System.out.println("   • \"Need to schedule a check-up for back pain\"");
        System.out.println();
        System.out.println("💡 COMMANDS:");
        System.out.println("   • 'web' - Show web interface information");
        System.out.println("   • 'demo' - Run automated test cases");
        System.out.println("   • 'help' - Show this help message");
        System.out.println("   • 'quit' - Exit the application");
        System.out.println("─".repeat(60));
    }

    // CHANGED: Made public for cluster access
    public static Behavior<Void> createWebEnabledBehavior() {
        return Behaviors.setup(context -> {
            context.getLog().info("🚀 Initializing Medical Triage Assistant with Web + Console...");
            
            // Initialize core infrastructure actors
            ActorRef<LogCommand> loggerRef = context.spawn(LoggerActor.create(), "logger");
            logger = loggerRef;
            
            ActorRef<SessionCommand> sessionActor = context.spawn(
                UserSessionActor.create(loggerRef), "session-manager");

            // Initialize processing actors
            ActorRef<RetrievalCommand> retrievalActor = context.spawn(
                RetrievalActor.create(loggerRef), "retrieval");
            ActorRef<LLMCommand> llmActor = context.spawn(
                LLMActor.create(loggerRef), "llm");

            // Initialize specialized care actors
            ActorRef<CareCommand> emergencyCare = context.spawn(
                EmergencyCareActor.create(loggerRef), "emergency-care");
            ActorRef<CareCommand> selfCare = context.spawn(
                SelfCareActor.create(loggerRef), "self-care");  
            ActorRef<CareCommand> appointmentCare = context.spawn(
                AppointmentActor.create(loggerRef), "appointment-care");

            // Initialize triage router
            ActorRef<TriageCommand> uiOrchestrator = context.spawn(
                TriageRouterActor.create(llmActor, retrievalActor, loggerRef, 
                                       emergencyCare, selfCare, appointmentCare, sessionActor), 
                "triage-router");

            // Initialize console user input handler
            ActorRef<UserInputCommand> userInputRef = context.spawn(
                UserInputActor.create(uiOrchestrator, loggerRef, sessionActor), 
                "user-input");
            userInputActor = userInputRef;

            loggerRef.tell(new LogEvent("SYSTEM", "MainSystem", 
                "All actors initialized successfully", "INFO"));

            // Start HTTP server
            context.getLog().info("🌐 Starting HTTP server...");
            
            try {
                HttpServer httpServer = new HttpServer(context.getSystem(), uiOrchestrator);
                httpServer.start("localhost", 8080)
                    .whenComplete((binding, throwable) -> {
                        if (throwable == null) {
                            System.out.println("\n🌐 ✅ WEB SERVER STARTED SUCCESSFULLY!");
                            System.out.println("🌐 Open your browser: http://localhost:8080");
                            System.out.println("🌐 Web interface now connected to FULL ACTOR SYSTEM!");
                            System.out.println("🤖 Features: LLM + Vector DB + Specialized Care Actors");
                            
                            loggerRef.tell(new LogEvent("SYSTEM", "HttpServer", 
                                "Web interface connected to actor system at http://localhost:8080", "INFO"));
                        } else {
                            System.err.println("❌ HTTP Server failed to start: " + throwable.getMessage());
                            throwable.printStackTrace();
                        }
                    });
                    
            } catch (Exception e) {
                System.err.println("❌ HTTP server initialization error: " + e.getMessage());
                e.printStackTrace();
            }

            // Mark system as ready
            systemReady = true;
            
            context.getLog().info("✅ Medical Triage Assistant ready - Console + Web");

            return Behaviors.empty();
        });
    }

    /**
     * ADDED: Cluster-enabled behavior - optionally disables web interface on service nodes
     * This method enables true multi-node deployment while preserving all functionality
     */
    public static Behavior<Void> createClusterEnabledBehavior(boolean enableWebInterface) {
        return Behaviors.setup(context -> {
            context.getLog().info("🚀 Initializing Medical Triage Assistant - Cluster Node (Web: {})", enableWebInterface);
            
            // Initialize core infrastructure actors (identical to original)
            ActorRef<LogCommand> loggerRef = context.spawn(LoggerActor.create(), "logger");
            logger = loggerRef;
            
            ActorRef<SessionCommand> sessionActor = context.spawn(
                UserSessionActor.create(loggerRef), "session-manager");

            // Initialize processing actors
            ActorRef<RetrievalCommand> retrievalActor = context.spawn(
                RetrievalActor.create(loggerRef), "retrieval");
            ActorRef<LLMCommand> llmActor = context.spawn(
                LLMActor.create(loggerRef), "llm");

            // Initialize specialized care actors
            ActorRef<CareCommand> emergencyCare = context.spawn(
                EmergencyCareActor.create(loggerRef), "emergency-care");
            ActorRef<CareCommand> selfCare = context.spawn(
                SelfCareActor.create(loggerRef), "self-care");  
            ActorRef<CareCommand> appointmentCare = context.spawn(
                AppointmentActor.create(loggerRef), "appointment-care");

            // Initialize triage router (central orchestrator)
            ActorRef<TriageCommand> uiOrchestrator = context.spawn(
                TriageRouterActor.create(llmActor, retrievalActor, loggerRef, 
                                       emergencyCare, selfCare, appointmentCare, sessionActor), 
                "triage-router");

            // Initialize console user input handler
            ActorRef<UserInputCommand> userInputRef = context.spawn(
                UserInputActor.create(uiOrchestrator, loggerRef, sessionActor), 
                "user-input");
            userInputActor = userInputRef;

            loggerRef.tell(new LogEvent("SYSTEM", "ClusterNode", 
                "All actors initialized successfully on cluster node", "INFO"));

            // Only start web server on primary node (port 2551)
            if (enableWebInterface) {
                context.getLog().info("🌐 Starting HTTP server on primary cluster node...");
                
                try {
                    HttpServer httpServer = new HttpServer(context.getSystem(), uiOrchestrator);
                    httpServer.start("localhost", 8080)
                        .whenComplete((binding, throwable) -> {
                            if (throwable == null) {
                                System.out.println("\n🌐 ✅ WEB SERVER STARTED ON PRIMARY CLUSTER NODE!");
                                System.out.println("🌐 Open your browser: http://localhost:8080");
                                System.out.println("🌐 Web interface connected to distributed actor system!");
                                
                                loggerRef.tell(new LogEvent("SYSTEM", "HttpServer", 
                                    "Web interface connected to cluster node", "INFO"));
                            } else {
                                System.err.println("❌ HTTP Server failed: " + throwable.getMessage());
                                throwable.printStackTrace();
                            }
                        });
                        
                } catch (Exception e) {
                    System.err.println("❌ HTTP server error: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                context.getLog().info("⚙️ Service node - web interface disabled");
                System.out.println("⚙️ Service node ready - processing medical queries through cluster");
            }

            // Mark system as ready
            systemReady = true;
            context.getLog().info("✅ Medical Triage Cluster Node Ready");

            return Behaviors.empty();
        });
    }
}