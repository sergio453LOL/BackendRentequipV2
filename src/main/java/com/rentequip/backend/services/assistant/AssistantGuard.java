package com.rentequip.backend.services.assistant;

import com.rentequip.backend.dtos.request.AssistantChatRequest.ChatTurn;
import com.rentequip.backend.dtos.response.EquipmentSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Guardrails that run before any language model is called, so greetings, spam, off-topic questions and
 * attempts to rewrite the assistant's rules are answered locally and never spend API tokens. Only a
 * question about machinery, the catalogue or renting on RentEquip goes on to the model.
 */
@Component
public class AssistantGuard {

    /** Longest question the model sees; longer ones are usually pasted text, not a rental question. */
    static final int MAX_QUESTION_LENGTH = 300;
    /** A short question right after an on-topic one ("¿y en Arequipa?") is taken as a follow-up. */
    private static final int MAX_FOLLOW_UP_LENGTH = 80;

    static final String TOO_LONG = "Tu mensaje es muy largo. Cuéntame en una o dos frases qué trabajo vas a hacer "
            + "y en qué ciudad, y te recomiendo el equipo.";
    static final String NOT_UNDERSTOOD = "No logré entender tu mensaje. Cuéntame qué trabajo vas a hacer en la obra "
            + "(por ejemplo excavar, compactar o levantar carga) y en qué ciudad.";
    static final String GREETING = "¡Hola! Soy el asistente de RentEquip. Dime qué trabajo vas a hacer en tu obra "
            + "y en qué ciudad, y te recomiendo maquinaria del catálogo.";
    static final String THANKS = "¡Con gusto! Si necesitas otro equipo, cuéntame la tarea y la ciudad de la obra.";
    static final String OFF_TOPIC = "Solo puedo ayudarte a elegir maquinaria y herramientas para alquilar en RentEquip. "
            + "Cuéntame qué trabajo vas a hacer en la obra y en qué ciudad.";
    static final String INJECTION = "No puedo cambiar mis instrucciones. Puedo ayudarte a elegir maquinaria del "
            + "catálogo: cuéntame qué trabajo vas a hacer y en qué ciudad.";

    private static final Pattern GREETING_ONLY = Pattern.compile(
            "^[\\s¡¿]*(hola|holi|buenas|buenos dias|buenas tardes|buenas noches|hey|hi|hello|que tal|saludos)[\\s!.?¡¿]*$");
    private static final Pattern THANKS_ONLY = Pattern.compile(
            "^[\\s¡¿]*(gracias|muchas gracias|ok|okay|vale|listo|perfecto|genial|chau|adios|bye|thanks)[\\s!.?¡¿]*$");
    /** Same letter four or more times ("aaaa"), or only laughter, typical of messages typed to test the bot. */
    private static final Pattern REPEATED = Pattern.compile("(\\p{L})\\1{3,}|^(ja|je|ji|ha|xd|lol)+$");
    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "\\bignora|\\bignore\\b|\\bolvida (las|tus|todas)|\\bforget (your|all|previous)"
                    + "|instrucciones (anteriores|previas|del sistema)|system prompt|prompt del sistema"
                    + "|tus instrucciones|your instructions|\\bactua como\\b|\\bact as\\b|haz de cuenta que"
                    + "|jailbreak|modo desarrollador|developer mode");

    /**
     * Word beginnings about construction, machinery and renting on the platform. They match only at the start
     * of a word, so "rent" never matches "diferente". A city alone is not enough: "el clima en Lima" is off topic.
     */
    private static final Pattern DOMAIN_WORD = Pattern.compile("\\b(maquin|equipo|herramient|alquil|arrend"
            + "|rentequip|reserv|obra|construc|terreno|suelo|tierra|zanja|excav|nivel|compact|demol|perfor|carga"
            + "|levant|izar|transport|acarre|concreto|cemento|hormigon|asfalt|paviment|carretera|pista|vereda"
            + "|edific|cimient|cimenta|muro|techo|losa|altura|andamio|generador|energia|motor|tonelad|precio"
            + "|tarifa|costo|cuesta|cobra|pago|pagar|deposito|garantia|disponib|entrega|devol|catalogo|flota"
            + "|operador|combustible|miner|mina|agricol|taladr|martill|sierra|cortadora|soldad|bomba|vibrador|mezcl"
            + "|rentabil|anunci|publica|solicitud|ingreso|ganancia|ocupacion|arrendador|tractor|excavadora|grua)");

    /**
     * Checks that need neither the catalogue nor the model: length, noise, small talk and prompt injection.
     * Empty when the question may go on.
     */
    public Optional<AssistantAnswer> screen(String question) {
        String text = CatalogKeywordRecommender.normalize(question).strip();
        if (question.strip().length() > MAX_QUESTION_LENGTH) return answer(TOO_LONG);
        if (text.chars().filter(Character::isLetter).count() < 3 || REPEATED.matcher(text).find()) {
            return answer(NOT_UNDERSTOOD);
        }
        if (GREETING_ONLY.matcher(text).matches()) return answer(GREETING);
        if (THANKS_ONLY.matcher(text).matches()) return answer(THANKS);
        if (INJECTION_PATTERN.matcher(text).find()) return answer(INJECTION);
        return Optional.empty();
    }

    /**
     * True when the question is about renting machinery: it uses a domain word, names something in the
     * catalogue, or is a short follow-up to an earlier question that was on topic.
     */
    public boolean isOnTopic(List<ChatTurn> history, List<EquipmentSummaryResponse> catalog) {
        String question = history.get(history.size() - 1).content();
        if (mentionsDomain(question, catalog)) return true;
        if (question.strip().length() > MAX_FOLLOW_UP_LENGTH) return false;
        List<ChatTurn> earlierQuestions = history.subList(0, history.size() - 1).stream()
                .filter(turn -> "user".equals(turn.role()))
                .toList();
        return !earlierQuestions.isEmpty()
                && mentionsDomain(earlierQuestions.get(earlierQuestions.size() - 1).content(), catalog);
    }

    private static boolean mentionsDomain(String question, List<EquipmentSummaryResponse> catalog) {
        String text = CatalogKeywordRecommender.normalize(question);
        if (DOMAIN_WORD.matcher(text).find() || CatalogKeywordRecommender.mentionsTask(text)) {
            return true;
        }
        // Brands and models of the catalogue ("CAT", "Bobcat 5650") count as on topic too.
        Set<String> words = new HashSet<>(Arrays.asList(text.split("[^a-z0-9]+")));
        return catalog.stream()
                .flatMap(item -> Stream.of(item.name(), item.brand(), item.model()))
                .filter(value -> value != null)
                .flatMap(value -> Stream.of(CatalogKeywordRecommender.normalize(value).split("[^a-z0-9]+")))
                .anyMatch(word -> word.length() >= 3 && words.contains(word));
    }

    private static Optional<AssistantAnswer> answer(String reply) {
        return Optional.of(new AssistantAnswer(reply, List.of()));
    }
}
