package com.rentequip.backend.services.assistant;

import com.rentequip.backend.dtos.response.EquipmentSummaryResponse;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Offline recommender used when the language model is not available. It maps the task the user describes
 * ("nivelar un terreno", "excavar zanjas") to the kinds of machine that do it, and scores the catalogue
 * by those terms plus the words of the question and the city it mentions.
 */
@Component
public class CatalogKeywordRecommender {

    private static final int MAX_RECOMMENDATIONS = 3;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Set<String> STOP_WORDS = Set.of("que", "para", "una", "uno", "los", "las", "del", "con",
            "por", "me", "mi", "un", "el", "la", "de", "en", "y", "o", "equipo", "maquina", "maquinaria", "necesito",
            "recomiendas", "recomienda", "quiero", "busco", "alquilar", "obra", "como", "cual", "hay", "tienen");

    /** Task described by the user -> machine names that perform it. */
    private static final Map<String, List<String>> TASK_TERMS = Map.ofEntries(
            Map.entry("nivel", List.of("motoniveladora", "niveladora", "tractor", "bulldozer", "oruga")),
            Map.entry("explan", List.of("motoniveladora", "tractor", "bulldozer")),
            Map.entry("terreno", List.of("motoniveladora", "tractor", "bulldozer", "excavadora", "retroexcavadora")),
            Map.entry("excav", List.of("excavadora", "retroexcavadora", "miniexcavadora")),
            Map.entry("zanja", List.of("excavadora", "retroexcavadora", "zanjadora")),
            Map.entry("cimenta", List.of("excavadora", "retroexcavadora", "perforadora")),
            Map.entry("compact", List.of("rodillo", "compactadora", "vibro", "plancha")),
            Map.entry("asfalt", List.of("rodillo", "pavimentadora", "compactadora")),
            Map.entry("paviment", List.of("rodillo", "pavimentadora", "compactadora")),
            Map.entry("levant", List.of("grua", "manipulador", "telehandler", "montacargas", "elevador")),
            Map.entry("izar", List.of("grua", "manipulador", "telehandler")),
            Map.entry("altura", List.of("grua", "elevador", "plataforma", "telehandler", "andamio")),
            Map.entry("transport", List.of("volquete", "camion", "dumper", "cargador")),
            Map.entry("acarre", List.of("volquete", "camion", "dumper", "cargador")),
            Map.entry("cargar", List.of("cargador", "minicargador", "retroexcavadora")),
            Map.entry("concreto", List.of("mezcladora", "mixer", "bomba", "trompo")),
            Map.entry("hormigon", List.of("mezcladora", "mixer", "bomba")),
            Map.entry("demol", List.of("martillo", "demoledor", "rompedor", "excavadora")),
            Map.entry("perfor", List.of("perforadora", "taladro", "martillo")),
            Map.entry("energia", List.of("generador", "grupo electrogeno")),
            Map.entry("electric", List.of("generador", "grupo electrogeno")));

    public AssistantAnswer recommend(String question, List<EquipmentSummaryResponse> catalog) {
        String normalizedQuestion = normalize(question);
        Set<String> terms = new HashSet<>();
        TASK_TERMS.forEach((task, machines) -> {
            if (normalizedQuestion.contains(task)) terms.addAll(machines);
        });
        for (String word : normalizedQuestion.split("[^a-z0-9]+")) {
            if (word.length() > 2 && !STOP_WORDS.contains(word)) terms.add(word);
        }
        String city = catalog.stream()
                .map(EquipmentSummaryResponse::city)
                .filter(name -> name != null && normalizedQuestion.contains(normalize(name)))
                .findFirst()
                .orElse(null);

        List<EquipmentSummaryResponse> ranked = catalog.stream()
                .map(item -> new Scored(item, score(item, terms, city)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(MAX_RECOMMENDATIONS)
                .map(Scored::item)
                .toList();

        if (ranked.isEmpty()) {
            return new AssistantAnswer("No encontré en el catálogo un equipo que encaje con lo que describes. "
                    + "Prueba contándome la tarea (por ejemplo excavar, compactar o levantar carga) y la ciudad de la obra.",
                    List.of());
        }
        String names = String.join(", ", ranked.stream().map(EquipmentSummaryResponse::name).toList());
        String where = city != null ? " en " + city : "";
        return new AssistantAnswer("Según lo que necesitas, te sugiero revisar" + where + ": " + names
                + ". Abre cada ficha para ver especificaciones, disponibilidad y solicitar la reserva.",
                ranked.stream().map(EquipmentSummaryResponse::id).toList());
    }

    private int score(EquipmentSummaryResponse item, Set<String> terms, String city) {
        String haystack = normalize(item.name() + " " + item.brand() + " " + item.model());
        int score = 0;
        for (String term : terms) {
            if (haystack.contains(term)) score += 3;
        }
        if (score > 0 && city != null && city.equalsIgnoreCase(item.city())) {
            score += 2;
        }
        return score;
    }

    private static String normalize(String text) {
        String decomposed = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private record Scored(EquipmentSummaryResponse item, int score) {
    }
}
