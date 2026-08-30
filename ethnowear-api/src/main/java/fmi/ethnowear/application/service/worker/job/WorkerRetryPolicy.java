package fmi.ethnowear.application.service.worker.job;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class WorkerRetryPolicy {

    public static final int INITIAL_BACKOFF_SECONDS = 30;
    public static final int MAXIMUM_BACKOFF_SECONDS = 300;

    public Duration delay(int attempt) {
        long seconds = Math.min(
                (long) Math.max(attempt, 1) * INITIAL_BACKOFF_SECONDS,
                MAXIMUM_BACKOFF_SECONDS
        );

        return Duration.ofSeconds(seconds);
    }
}