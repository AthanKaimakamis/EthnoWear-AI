package fmi.ethnowear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ethnowear.conversation.security")
public record ConversationSecurityProperties(int maximumTurnsPerMinute, int maximumTrackedOwners) {

    public ConversationSecurityProperties {
        if (maximumTurnsPerMinute < 1)
            throw new IllegalStateException("Maximum conversation turns per minute must be positive");

        if (maximumTrackedOwners < 1)
            throw new IllegalStateException("Maximum tracked conversation owners must be positive");
    }
}