package com.triage;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.actors.*;
import com.triage.messages.Messages.*;

import java.util.Scanner;

/**
 * Medical Triage Assistant - Interactive Chatbot Mode
 * Simple single-node version that avoids all cluster serialization issues
 * Demonstrates all required Akka communication patterns with real user interaction
 */
public class Main {
    
    private static ActorRef<UserInputCommand> userInputActor;
    private static ActorRef<LogCommand> logger;
    private static boolean systemReady = false;
    
    public static void main(String[] args) {
        System.out.println("🏥 Initializing Medical Triage Assistant...");
        
        // Create simple ActorSystem without cluster configuration
        ActorSystem<Void> system = ActorSystem.create(
            createSimpleMainBehavior(),
            "MedicalTriageSystem"  // Simple name, no cluster config
        );

        // Wait for system initialization
        waitForSystemReady();
        
        // Start interactive chatbot
        startInteractiveChatbot(system);
    }
    
    private static void waitForSystemReady() {
        System.out.println("⏳ Starting up all actors...");
        try {
            while (!systemReady) {
                Thread.sleep(500);
            }
            Thread.sleep(2000); // Additional time for full initialization
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void startInteractiveChatbot(ActorSystem<Void> system) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\n🏥 ============================================");
        System.out.println("🏥 MEDICAL TRIAGE ASSISTANT - CHATBOT MODE");
        System.out.println("🏥 ============================================");
        System.out.println("💬 I'm your AI-powered medical triage assistant.");
        System.out.println("💬 Describe your symptoms and I'll help assess your situation.");
        System.out.println("💬 Commands: 'quit' to exit, 'help' for guidance, 'demo' for automated tests\n");

        showWelcomeMessage();
        
        while (true) {
            System.out.print("\n🩺 You: ");
            String userInput = scanner.nextLine().trim();
            
            // Handle special commands
            if (userInput.equalsIgnoreCase("quit") || userInput.equalsIgnoreCase("exit")) {
                System.out.println("\n👋 Thank you for using Medical Triage Assistant!");
                System.out.println("⚠️  Remember: This is for educational purposes only.");
                System.out.println("   Always consult real medical professionals for health concerns.");
                break;
            }
            
            if (userInput.equalsIgnoreCase("help")) {
                showHelp();
                continue;
            }
            
            if (userInput.equalsIgnoreCase("demo")) {
                System.out.println("\n🧪 Running automated demo tests...");
                runAutomatedDemo();
                System.out.println("\n💬 Demo completed! You can now continue with manual input.");
                continue;
            }
            
            if (userInput.isEmpty()) {
                System.out.println("💡 Please describe your symptoms, or type 'help' for guidance.");
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
        
        // Demo summary
        logger.tell(new LogEvent("DEMO", "TestSuite", 
            "📊 DEMO SUMMARY: All communication patterns demonstrated", "INFO"));
        logger.tell(new LogEvent("DEMO", "TestSuite", 
            "✅ TELL: UserInput → Router, Router → Logger", "INFO"));
        logger.tell(new LogEvent("DEMO", "TestSuite", 
            "✅ ASK: Router ↔ LLM, Router ↔ Retrieval", "INFO"));
        logger.tell(new LogEvent("DEMO", "TestSuite", 
            "✅ FORWARD: Router → Care Actors (preserving sender)", "INFO"));
    }
    
    private static void showWelcomeMessage() {
        System.out.println("🎯 SYSTEM CAPABILITIES:");
        System.out.println("   • Real-time AI symptom analysis using Gemini");
        System.out.println("   • Distributed actor processing (tell/ask/forward patterns)");
        System.out.println("   • Intelligent triage routing (Emergency/Self-Care/Appointment)");
        System.out.println("   • Session tracking and conversation history");
        System.out.println("   • Comprehensive medical knowledge base");
    }
    
    private static void showHelp() {
        System.out.println("\n📋 HOW TO USE THE MEDICAL TRIAGE ASSISTANT:");
        System.out.println("─".repeat(60));
        System.out.println("📝 DESCRIBE YOUR SYMPTOMS clearly and specifically");
        System.out.println();
        System.out.println("🔍 GOOD EXAMPLES:");
        System.out.println("   • \"I have severe chest pain and shortness of breath\"");
        System.out.println("   • \"Mild headache and runny nose for 2 days, no fever\"");
        System.out.println("   • \"Sharp stomach pain that started after eating\"");
        System.out.println("   • \"Persistent cough for a week with yellow phlegm\"");
        System.out.println("   • \"Need to schedule a check-up for back pain\"");
        System.out.println();
        System.out.println("🎯 THE SYSTEM WILL:");
        System.out.println("   • Retrieve relevant medical knowledge");
        System.out.println("   • Analyze symptoms using AI (Gemini)");
        System.out.println("   • Classify urgency level");
        System.out.println("   • Route to appropriate care level");
        System.out.println("   • Provide specific recommendations");
        System.out.println();
        System.out.println("🤖 TECHNICAL FEATURES DEMONSTRATED:");
        System.out.println("   • TELL: Fire-and-forget messaging");
        System.out.println("   • ASK: Request-response patterns");
        System.out.println("   • FORWARD: Message forwarding with sender preservation");
        System.out.println("   • Distributed actor-based processing");
        System.out.println();
        System.out.println("💡 COMMANDS:");
        System.out.println("   • 'demo' - Run automated test cases");
        System.out.println("   • 'help' - Show this help message");
        System.out.println("   • 'quit' - Exit the application");
        System.out.println();
        System.out.println("⚠️  IMPORTANT DISCLAIMER:");
        System.out.println("    This is an educational project demonstrating distributed systems.");
        System.out.println("    Always consult real medical professionals for health issues.");
        System.out.println("─".repeat(60));
    }

    private static Behavior<Void> createSimpleMainBehavior() {
        return Behaviors.setup(context -> {
            context.getLog().info("🚀 Initializing Medical Triage Assistant (Simple Mode)...");
            
            // Initialize core infrastructure actors
            ActorRef<LogCommand> loggerRef = context.spawn(LoggerActor.create(), "logger");
            logger = loggerRef; // Store reference for interactive mode
            
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

            // Initialize triage router with all dependencies
            ActorRef<TriageCommand> triageRouter = context.spawn(
                TriageRouterActor.create(llmActor, retrievalActor, loggerRef, 
                                       emergencyCare, selfCare, appointmentCare), 
                "triage-router");

            // Initialize user input handler
            ActorRef<UserInputCommand> userInputRef = context.spawn(
                UserInputActor.create(triageRouter, loggerRef, sessionActor), 
                "user-input");
            userInputActor = userInputRef; // Store reference for interactive mode

            loggerRef.tell(new LogEvent("SYSTEM", "MainSystem", 
                "All actors initialized successfully", "INFO"));

            // Mark system as ready
            systemReady = true;
            
            context.getLog().info("✅ Interactive Medical Triage Assistant ready for user input");

            return Behaviors.empty();
        });
    }
}