package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import polaris.hotel.games.GameTexts;

/**
 * The quiz's questions in each language, read once from `hall/quiz-<language>.json` in the emulator: a list of
 * [category, question, [four answers], index of the right one]. The hotel's language is the setting
 * `hall.quiz.language` (en, fr, de, es, it, nl, ar; English when unknown). To add questions: add lines to every
 * file, in the same order, and rebuild.
 */
public final class QuizBank {

    public static final List<String> LANGUAGES = List.of("en", "fr", "de", "es", "it", "nl", "ar");

    private static final Map<String, List<Quiz.Question>> QUESTIONS =
            LANGUAGES.stream().collect(Collectors.toUnmodifiableMap(language -> language, QuizBank::load));

    private QuizBank() {}

    /** The questions in a language (English when it has none). */
    public static List<Quiz.Question> questions(String language) {
        List<Quiz.Question> questions =
                QUESTIONS.get(language == null ? "" : language.trim().toLowerCase());

        return questions == null || questions.isEmpty() ? QUESTIONS.get("en") : questions;
    }

    /** The questions in the hotel's language. */
    public static List<Quiz.Question> questions() {
        return questions(GameTexts.setting("hall.quiz.language", "en"));
    }

    private static List<Quiz.Question> load(String language) {
        try (InputStream input =
                QuizBank.class.getClassLoader().getResourceAsStream("hall/quiz-" + language + ".json")) {
            if (input == null) return List.of();

            List<Quiz.Question> questions = new ArrayList<>();
            JsonArray all = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonArray();

            for (JsonElement element : all) {
                JsonArray entry = element.getAsJsonArray();
                List<String> answers = new ArrayList<>();

                for (JsonElement answer : entry.get(2).getAsJsonArray()) answers.add(answer.getAsString());

                questions.add(new Quiz.Question(
                        entry.get(0).getAsString(),
                        entry.get(1).getAsString(),
                        List.copyOf(answers),
                        entry.get(3).getAsInt()));
            }

            return List.copyOf(questions);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }
}
