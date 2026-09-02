package fmi.ethnowear.application.service.conversation.policy;

import fmi.ethnowear.application.exception.ConversationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import static fmi.ethnowear.util.TextUtils.normalizeSearchText;
import static fmi.ethnowear.application.conversation.policy.ConversationPolicyTerms.*;

@Component
public class ConversationInputPolicy {

    public void validate(String input, boolean hasPreviousTurns) {
        String normalized = normalizeSearchText(input);

        if (FORBIDDEN_PHRASES.stream().anyMatch(normalized::contains))
            throw new ConversationException(
                    HttpStatus.BAD_REQUEST,
                    "CONVERSATION_UNSAFE_INPUT",
                    "The request contains unsupported instructions"
            );

        if (DOMAIN_TERMS.matcher(normalized).find())
            return;

        if (GREETINGS.contains(normalized))
            return;

        if (hasPreviousTurns
                && FOLLOW_UP_TERMS.matcher(normalized).find())
            return;

        throw new ConversationException(
                HttpStatus.BAD_REQUEST,
                "CONVERSATION_OUT_OF_SCOPE",
                "The assistant answers questions about Bulgarian embroidery, clothing and cultural heritage"
        );
    }
}