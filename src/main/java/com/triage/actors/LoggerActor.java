package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;

import java.time.format.DateTimeFormatter;

/**
 * LoggerActor - Centralized system logging
 * Demonstrates TELL pattern - receives fire-and-forget logging messages
 */
public class LoggerActor extends AbstractBehavior<LogCommand> {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public static Behavior<LogCommand> create() {
        return Behaviors.setup(LoggerActor::new);
    }

    private LoggerActor(ActorContext<LogCommand> context) {
        super(context);
        getContext().getLog().info("📝 LoggerActor initialized");
    }

    @Override
    public Receive<LogCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(LogEvent.class, this::onLogEvent)
                .build();
    }

    private Behavior<LogCommand> onLogEvent(LogEvent msg) {
        String timestamp = msg.timestamp.atZone(java.time.ZoneId.systemDefault())
            .format(TIMESTAMP_FORMAT);
        
        String logLevel = msg.level.toUpperCase();
        String emoji = getEmojiForLevel(logLevel);
        
        String logMessage = String.format("[%s] %s %s | Session: %s | %s: %s",
            timestamp, emoji, logLevel, msg.sessionId, msg.actorName, msg.event);
        
        // Use appropriate log level
        switch (logLevel) {
            case "ERROR":
                getContext().getLog().error(logMessage);
                break;
            case "WARNING":
                getContext().getLog().warn(logMessage);
                break;
            case "DEBUG":
                getContext().getLog().debug(logMessage);
                break;
            case "CRITICAL":
                getContext().getLog().error("🚨 CRITICAL: {}", logMessage);
                break;
            default:
                getContext().getLog().info(logMessage);
        }
        
        return this;
    }

    private String getEmojiForLevel(String level) {
        return switch (level) {
            case "ERROR" -> "❌";
            case "WARNING" -> "⚠️";
            case "DEBUG" -> "🔍";
            case "CRITICAL" -> "🚨";
            default -> "ℹ️";
        };
    }
}