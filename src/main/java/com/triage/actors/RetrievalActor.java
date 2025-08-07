package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;

import java.util.HashMap;
import java.util.Map;

/**
 * RetrievalActor - Medical Knowledge Database
 * Demonstrates response handling for ASK pattern
 * Responsibility: Provide relevant medical context for symptom analysis
 * Future: Will integrate with ChromaDB vector database
 */
public class RetrievalActor extends AbstractBehavior<RetrievalCommand> {

    private final ActorRef<LogCommand> logger;
    private final Map<String, String> medicalKnowledge;

    public static Behavior<RetrievalCommand> create(ActorRef<LogCommand> logger) {
        return Behaviors.setup(context -> new RetrievalActor(context, logger));
    }

    private RetrievalActor(ActorContext<RetrievalCommand> context, ActorRef<LogCommand> logger) {
        super(context);
        this.logger = logger;
        this.medicalKnowledge = initializeMedicalKnowledge();
        
        getContext().getLog().info("🔍 RetrievalActor initialized with {} knowledge entries", 
            medicalKnowledge.size());
    }

    @Override
    public Receive<RetrievalCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(RetrieveContext.class, this::onRetrieveContext)
                .build();
    }

    private Behavior<RetrievalCommand> onRetrieveContext(RetrieveContext msg) {
        getContext().getLog().info("🔍 Retrieving context for session [{}]: {}", 
            msg.sessionId, msg.symptoms);
        
        logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
            "Searching medical knowledge base", "INFO"));

        // Simulate vector database search
        String context = searchMedicalKnowledge(msg.symptoms);
        
        logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
            "Context retrieved: " + context.length() + " characters", "DEBUG"));

        // Respond back to the ASK requester
        msg.replyTo.tell(new RetrievalResult(msg.sessionId, context, true));

        return this;
    }

    private String searchMedicalKnowledge(String symptoms) {
        String symptomsLower = symptoms.toLowerCase();
        StringBuilder contextBuilder = new StringBuilder();
        
        // Search through knowledge base for relevant entries
        for (Map.Entry<String, String> entry : medicalKnowledge.entrySet()) {
            if (symptomsLower.contains(entry.getKey())) {
                contextBuilder.append(entry.getValue()).append("\n\n");
            }
        }
        
        // If no specific match, provide general triage guidance
        if (contextBuilder.length() == 0) {
            contextBuilder.append(medicalKnowledge.get("general_triage"));
        }
        
        return contextBuilder.toString().trim();
    }

    private Map<String, String> initializeMedicalKnowledge() {
        Map<String, String> knowledge = new HashMap<>();
        
        // Emergency symptoms
        knowledge.put("chest pain", """
            CHEST PAIN ASSESSMENT:
            - Sudden, severe chest pain may indicate heart attack, pulmonary embolism, or aortic dissection
            - Crushing, squeezing sensation often associated with cardiac events
            - Pain radiating to arm, jaw, or back requires immediate evaluation
            - Associated symptoms: shortness of breath, nausea, sweating
            - IMMEDIATE MEDICAL ATTENTION REQUIRED
            """);
        
        knowledge.put("shortness of breath", """
            BREATHING DIFFICULTY ASSESSMENT:
            - Sudden onset may indicate pulmonary embolism, pneumothorax, or cardiac event
            - Gradual onset could suggest asthma, COPD exacerbation, or heart failure
            - Associated chest pain increases urgency
            - Unable to speak in full sentences indicates severe distress
            - URGENT EVALUATION NEEDED
            """);
        
        knowledge.put("severe pain", """
            SEVERE PAIN ASSESSMENT:
            - Pain scale 8-10/10 requires immediate attention
            - Sudden onset severe pain may indicate medical emergency
            - Abdominal pain with rigidity suggests surgical emergency
            - Severe headache with neck stiffness may indicate meningitis
            - IMMEDIATE MEDICAL EVALUATION
            """);

        // Moderate symptoms
        knowledge.put("headache", """
            HEADACHE ASSESSMENT:
            - Tension headaches are common and usually manageable with rest and OTC medications
            - Migraines may require specific treatments and lifestyle modifications
            - New or severe headaches, especially with fever or neck stiffness, need evaluation
            - Chronic headaches should be evaluated by healthcare provider
            - Most headaches can be managed with conservative care
            """);
        
        knowledge.put("fever", """
            FEVER ASSESSMENT:
            - Low-grade fever (99-101°F) often indicates viral infection
            - High fever (>103°F) may require medical attention
            - Fever with severe symptoms needs evaluation
            - Fever in immunocompromised patients requires prompt care
            - Most viral fevers resolve with supportive care
            """);

        // Mild symptoms
        knowledge.put("cough", """
            COUGH ASSESSMENT:
            - Dry cough often viral, may last 2-3 weeks
            - Productive cough may indicate bacterial infection if persistent
            - Cough with fever, shortness of breath needs evaluation
            - Most coughs are self-limiting and resolve with time
            - Persistent cough >3 weeks should be evaluated
            """);
        
        knowledge.put("sore throat", """
            SORE THROAT ASSESSMENT:
            - Most sore throats are viral and self-limiting
            - Strep throat may require antibiotic treatment
            - Difficulty swallowing or breathing requires immediate attention
            - Most improve with rest, fluids, and supportive care
            - Persistent symptoms >1 week should be evaluated
            """);

        // General triage guidance
        knowledge.put("general_triage", """
            GENERAL TRIAGE PRINCIPLES:
            - Life-threatening symptoms require immediate emergency care
            - Severe or worsening symptoms need urgent medical evaluation
            - Moderate symptoms may benefit from healthcare consultation
            - Mild symptoms often resolve with supportive care and time
            - When in doubt, seek professional medical advice
            - Consider patient's overall health, age, and risk factors
            """);

        return knowledge;
    }
}