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

/**
 * The quiz's questions, read once from `hall/quiz-fr.json` in the emulator: a list of [category, question, [four
 * answers], index of the right one]. To add questions: add lines there and rebuild.
 */
public final class QuizBank {

    public static final List<Quiz.Question> QUESTIONS = load();

    private QuizBank() {}

    private static List<Quiz.Question> load() {
        try (InputStream input = QuizBank.class.getClassLoader().getResourceAsStream("hall/quiz-fr.json")) {
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
