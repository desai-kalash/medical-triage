package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;

/**
 * EmergencyCareActor - Handles urgent medical situations
 * Demonstrates FORWARD pattern - receives messages forwarded from TriageRouter
 */
public class EmergencyCareActor extends AbstractBehavior<CareCommand> {

    private final ActorRef<LogCommand> logger;

    public static Behavior<CareCommand> create(ActorRef<LogCommand> logger) {
        return Behaviors.setup(context -> new EmergencyCareActor(context, logger));
    }

    private EmergencyCareActor(ActorContext<CareCommand> context, ActorRef<LogCommand> logger) {
        super(context);
        this.logger = logger;
    }

    @Override
    public Receive<CareCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(HandleTriageCase.class, this::onHandleTriageCase)
                .build();
    }

    private Behavior<CareCommand> onHandleTriageCase(HandleTriageCase msg) {
        getContext().getLog().info("🚨 EMERGENCY CASE for session [{}]", msg.sessionId);
        
        logger.tell(new LogEvent(msg.sessionId, "EmergencyCareActor", 
            "Processing emergency case", "CRITICAL"));

        String emergencyResponse = String.format("""
            🚨 EMERGENCY MEDICAL ATTENTION REQUIRED
            
            Based on your symptoms: %s
            Severity Assessment: %s
            
            IMMEDIATE ACTIONS REQUIRED:
            ⚡ Call 911 or emergency services immediately
            ⚡ Do NOT drive yourself - call ambulance if needed
            ⚡ If chest pain: Take aspirin if not allergic (unless told otherwise)
            ⚡ If breathing difficulty: Sit upright, loosen tight clothing
            ⚡ Stay calm and follow emergency dispatcher instructions
            
            MEDICAL ANALYSIS:
            %s
            
            WHY THIS IS URGENT:
            These symptoms may indicate serious conditions such as heart attack, stroke, 
            pulmonary embolism, or other life-threatening emergencies that require 
            immediate medical intervention. Time is critical for the best outcomes.
            
            ⚠️ This is a medical emergency - do not delay seeking professional care!
            """, msg.symptoms, msg.severity, msg.analysis);

        logger.tell(new LogEvent(msg.sessionId, "EmergencyCareActor", 
            "Emergency response generated", "INFO"));

        // Create proper response object
        TriageResponse response = new TriageResponse(
            msg.sessionId, 
            msg.symptoms, 
            "EMERGENCY", 
            emergencyResponse, 
            msg.severity, 
            true
        );

        // Send response back to original requester
        if (msg.originalSender != null) {
            msg.originalSender.tell(response);
        }
        
        return this; // Keep actor alive (no need for complex shutdown)
    }
}