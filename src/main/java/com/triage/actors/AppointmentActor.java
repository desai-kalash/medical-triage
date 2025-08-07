package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;

/**
 * AppointmentActor - Handles conditions requiring medical consultation
 */
public class AppointmentActor extends AbstractBehavior<CareCommand> {

    private final ActorRef<LogCommand> logger;

    public static Behavior<CareCommand> create(ActorRef<LogCommand> logger) {
        return Behaviors.setup(context -> new AppointmentActor(context, logger));
    }

    private AppointmentActor(ActorContext<CareCommand> context, ActorRef<LogCommand> logger) {
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
        getContext().getLog().info("📅 APPOINTMENT case for session [{}]", msg.sessionId);
        
        logger.tell(new LogEvent(msg.sessionId, "AppointmentActor", 
            "Processing appointment recommendation", "INFO"));

        String appointmentResponse = String.format("""
            📅 MEDICAL APPOINTMENT RECOMMENDED
            
            Session: %s
            Symptoms: %s
            Severity: %s
            
            NEXT STEPS:
            📞 Contact your primary care physician
            📞 Schedule appointment within 1-2 weeks (sooner if symptoms worsen)
            📞 If no regular doctor, consider urgent care or walk-in clinic
            
            PREPARATION FOR APPOINTMENT:
            📝 List all symptoms and when they started
            📝 Note what makes symptoms better or worse  
            📝 Bring list of current medications
            📝 Prepare questions for your healthcare provider
            
            Analysis: %s
            
            ⚠️ SEEK URGENT CARE IF:
            • Symptoms suddenly worsen
            • New concerning symptoms develop
            • You develop fever >101°F
            • You feel the situation has changed
            
            Early medical consultation can prevent complications.
            """, msg.sessionId, msg.symptoms, msg.severity, msg.analysis);

        logger.tell(new LogEvent(msg.sessionId, "AppointmentActor", 
            "Appointment guidance provided", "INFO"));

        TriageResponse response = new TriageResponse(
            msg.sessionId, 
            msg.symptoms, 
            "APPOINTMENT", 
            appointmentResponse, 
            msg.severity, 
            true
        );

        msg.originalSender.tell(response);
        
        return Behaviors.stopped();
    }
}