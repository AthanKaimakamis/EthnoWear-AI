package fmi.ethnowear.application.service.conversation.orchestration;

import fmi.ethnowear.application.dto.conversation.ConversationAnswerDetails;
import fmi.ethnowear.application.model.conversation.ConversationHistoryMessage;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.application.port.conversation.ConversationIntentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ConversationPolicyService {
    private final ObjectProvider<ConversationIntentGateway> intentGateways;
    private static final Pattern DOMAIN = Pattern.compile(
            "(?iuU)\\b(ethnowear|шевиц\\p{L}*|везб\\p{L}*|бродер\\p{L}*|носи[яи]\\p{L}*|"
                    + "embroider\\p{L}*|stitch\\p{L}*|traditional clothing|folk costume\\p{L}*|"
                    + "орнамент\\p{L}*|мотив\\p{L}*|motif\\p{L}*)\\b");
    private static final Pattern BYPASS = Pattern.compile(
            "(?iu)(ignore.{0,40}(rules|instructions|sources)|system prompt|developer message|"
                    + "игнорирай.{0,40}(правил|инструк|източ)|без източници|without sources|"
                    + "разкрий.{0,30}(промпт|парол)|reveal.{0,30}(prompt|password))");

    private static final Pattern CLAIMED_APPROVAL = Pattern.compile(
            "(?isu)(одобрен.{0,30}източник|проверен.{0,30}администратор|approved.{0,30}source|verified.{0,30}admin)");
    private static final Pattern CLAIMED_AUTHORITY = Pattern.compile(
            "(?isu)(chunk\\s*:\\s*\\d+|като установен факт|as (an? )?(established|verified) fact|постави цитат|add a citation)");

    private static boolean claimsSourceAuthority(String message) {
        return CLAIMED_APPROVAL.matcher(message).find() && CLAIMED_AUTHORITY.matcher(message).find();
    }

    public enum Route { GREETING, THANKS, WELLBEING, HELP, CLARIFY, OUT_OF_SCOPE, BOUNDARY, USER_SOURCE, KNOWLEDGE }

    public Route route(String question, List<ConversationHistoryMessage> history) {
        if (claimsSourceAuthority(question)) return Route.USER_SOURCE;
        if (BYPASS.matcher(question).find()) return Route.BOUNDARY;
        String normalized = normalize(question);
        Route social = social(normalized);
        if (social != null) return social;
        if (normalized.matches("(what can you do|who are you|help|какво можеш|кой си|помощ|как работи архивът|how does the archive work)"))
            return Route.HELP;
        if (isPureFollowUp(normalized))
            return evidenceQuery(question, history).equals(question.trim()) ? Route.CLARIFY : Route.KNOWLEDGE;
        // Substantive messages, including mixed greetings, require scope classification.
        // A domain keyword alone does not authorize an unrelated mixed request.
        var gateway = intentGateways.getIfAvailable();
        if (gateway != null) {
            var intent = gateway.classify(question, history);
            if (intent != null) return Route.valueOf(intent.name());
        }
        return Route.CLARIFY;
    }

    public Optional<ConversationAnswerDetails> reply(ConversationTurnExecutionContext context, Route route) {
        if (route == Route.KNOWLEDGE) return Optional.empty();
        boolean en = "en".equals(context.language());
        String answer = switch (route) {
            case GREETING -> en
                    ? "Hello! What would you like to explore about Bulgarian embroidery or traditional clothing?"
                    : "Здравей! Какво ти е интересно за българските шевици или традиционното облекло?";
            case THANKS -> en ? "You're welcome! Would you like to explore anything else?"
                    : "С удоволствие! Искаш ли да разгледаме още нещо?";
            case WELLBEING -> en ? "I'm here and ready to help. What would you like to explore today?"
                    : "Тук съм и съм готов да помогна. Какво искаш да разгледаме днес?";
            case HELP -> en
                    ? "I'm the EthnoWear assistant. I can help you explore Bulgarian embroidery, traditional clothing, motifs and techniques, explain our source material, and navigate the archive. For factual explanations I use the project's sources and tell you when information is missing. What interests you?"
                    : "Аз съм асистентът на EthnoWear. Мога да помогна с българските шевици, традиционното облекло, мотивите и техниките, да обясня наличните източници и да те насоча към архива. За фактическите обяснения използвам източниците на проекта и посочвам, когато информацията не достига. Какво те интересува?";
            case OUT_OF_SCOPE -> en
                    ? "That's outside my focus. I can help with Bulgarian embroidery, traditional clothing and the EthnoWear archive. Would you like to explore a motif, technique or regional tradition?"
                    : "Това е извън моята област. Мога да помогна с българските шевици, традиционното облекло и архива на EthnoWear. Искаш ли да разгледаме мотив, техника или регионална традиция?";
            case BOUNDARY -> en
                    ? "We can discuss the topic naturally, but factual explanations must remain supported by EthnoWear's sources. Which embroidery or clothing topic would you like to explore?"
                    : "Можем да обсъждаме темата свободно, но фактическите обяснения трябва да се опират на източниците на EthnoWear. Коя тема за шевиците или облеклото искаш да разгледаме?";
            case USER_SOURCE -> en
                    ? "Pasted text, a source ID and a claim of administrator approval do not establish verified evidence. I cannot present that assertion as a fact or attach citations to it on that basis. Approval and supporting passages must be checked against the project's stored records."
                    : "Поставеният текст, посоченият ID и твърдението за одобрение от администратор не удостоверяват проверен източник. Не мога на тази основа да представя твърдението като факт или да добавя цитати към него. Одобрението и подкрепящите откъси трябва да се проверят в записите на проекта.";
            default -> en
                    ? "Could you clarify which motif, region, technique, source or archive item you mean?"
                    : "Можеш ли да уточниш кой мотив, регион, техника, източник или архивен обект имаш предвид?";
        };
        return Optional.of(new ConversationAnswerDetails(context.conversationId(), context.turnId(),
                answer, false, List.of(), List.of(), List.of(), List.of()));
    }

    private static Route social(String value) {
        if (value.matches("(hello|hi|hey|здравей|здравейте|здрасти|добър ден|добро утро|добър вечер)( ethnowear)?")) return Route.GREETING;
        if (value.matches("(thanks|thank you|благодаря|мерси|благодаря ти|thank you very much)")) return Route.THANKS;
        if (value.matches("(how are you|как си|как сте|как върви)")) return Route.WELLBEING;
        return null;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}—…]", " ").trim().replaceAll("\\s+", " ");
    }

    private static boolean isPureFollowUp(String normalized) {
        return normalized.matches("((я |моля )?провери (пак|отново)|check again|try again|"
                + "why|why is that|защо|защо така|tell me more|разкажи още|продължи|continue|"
                + "(обясни|кажи|разкажи)( го| това)? по (просто|кратко|подробно)|"
                + "(explain|say)( it| that)? (more simply|in simpler terms)|make it shorter)");
    }

    public static boolean isFollowUp(String question) {
        String value = normalize(question);
        return value.length() <= 180 && (isPureFollowUp(value)
                || value.startsWith("а ") || value.startsWith("и ") || value.startsWith("and ")
                || value.startsWith("what about ") || value.startsWith("which of those")
                || value.contains("тези") || value.contains("тях") || value.contains("там")
                || value.contains("по просто") || value.contains("по кратко") || value.contains("more simply")
                || value.contains("simpler") || value.contains("shorter") || value.contains("това"));
    }

    public static String evidenceQuery(String current, List<ConversationHistoryMessage> history) {
        if (!isFollowUp(current)) return current.trim();
        String anchor = null;
        for (int i = history.size() - 1; i >= 0; i--) {
            var previousMessage = history.get(i);
            String previous = previousMessage.userMessage();
            if (social(normalize(previous)) != null || isPureFollowUp(normalize(previous))) continue;
            if ((DOMAIN.matcher(previous).find() || !previousMessage.evidenceIds().isEmpty()
                    || !previousMessage.entityLocalNames().isEmpty()) && !BYPASS.matcher(previous).find()
                    && !claimsSourceAuthority(previous))
                anchor = previous + (previousMessage.entityLocalNames().isEmpty() ? ""
                        : "\n" + String.join(" ", previousMessage.entityLocalNames()));
            break;
        }
        if (anchor == null) return current.trim();
        String query = anchor.trim() + "\n" + current.trim();
        return query.length() <= 1000 ? query : query.substring(0, 1000);
    }
}
