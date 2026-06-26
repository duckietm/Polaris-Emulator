package com.eu.habbo.habbohotel.translations;

import com.eu.habbo.Emulator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GoogleTranslateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleTranslateManager.class);
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final long CACHE_TTL_MS = 1000L * 60L * 60L * 6L;
    private static final int MAX_TRANSLATION_CACHE_SIZE = 2048;
    private static final int MAX_LANGUAGE_CACHE_SIZE = 32;
    private static final String FREE_TRANSLATE_ENDPOINT = "https://translate.googleapis.com/translate_a/single";
    private static final List<SupportedLanguage> FREE_SUPPORTED_LANGUAGES = buildFreeSupportedLanguages();

    private final Map<String, CachedTranslation> translationCache = Collections.synchronizedMap(
            new LinkedHashMap<String, CachedTranslation>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedTranslation> eldest) {
                    return this.size() > MAX_TRANSLATION_CACHE_SIZE;
                }
            });
    private final Map<String, CachedLanguages> languagesCache = Collections.synchronizedMap(
            new LinkedHashMap<String, CachedLanguages>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedLanguages> eldest) {
                    return this.size() > MAX_LANGUAGE_CACHE_SIZE;
                }
            });

    public SupportedLanguagesResponse getSupportedLanguages(String displayLanguage) {
        String normalizedDisplayLanguage = normalizeLanguageCode(displayLanguage, "en");

        CachedLanguages cachedLanguages = this.languagesCache.get(normalizedDisplayLanguage);

        if ((cachedLanguages != null) && !cachedLanguages.isExpired()) {
            return SupportedLanguagesResponse.success(new ArrayList<>(cachedLanguages.languages));
        }

        var supportedLanguages = new ArrayList<SupportedLanguage>(FREE_SUPPORTED_LANGUAGES);
        this.languagesCache.put(normalizedDisplayLanguage, new CachedLanguages(supportedLanguages));
        return SupportedLanguagesResponse.success(supportedLanguages);
    }

    public TranslationResponse translate(String text, String targetLanguage) {
        String safeText = text == null ? "" : text;
        String normalizedTargetLanguage = normalizeLanguageCode(targetLanguage, "en");

        if (safeText.trim().isEmpty()) {
            return TranslationResponse.success(safeText, safeText, "", normalizedTargetLanguage);
        }

        String cacheKey = normalizedTargetLanguage + '\u0000' + safeText;
        CachedTranslation cachedTranslation = this.translationCache.get(cacheKey);

        if ((cachedTranslation != null) && !cachedTranslation.isExpired()) {
            return cachedTranslation.response;
        }

        try {
            String requestUrl = FREE_TRANSLATE_ENDPOINT
                    + "?client=gtx"
                    + "&sl=auto"
                    + "&tl=" + encode(normalizedTargetLanguage)
                    + "&dt=t"
                    + "&q=" + encode(safeText);
            HttpsURLConnection connection = this.openGet(requestUrl);

            int statusCode = connection.getResponseCode();

            if (statusCode != 200) {
                return TranslationResponse.failure(safeText, normalizedTargetLanguage, this.readErrorMessage(connection));
            }

            JsonArray response = this.readJsonArray(connection.getInputStream());
            JsonArray translatedParts = response.size() > 0 && response.get(0).isJsonArray()
                    ? response.get(0).getAsJsonArray()
                    : new JsonArray();
            var translatedText = new StringBuilder();

            for (int index = 0; index < translatedParts.size(); index++) {
                if (!translatedParts.get(index).isJsonArray()) {
                    continue;
                }

                JsonArray translatedPart = translatedParts.get(index).getAsJsonArray();

                if (translatedPart.size() > 0 && !translatedPart.get(0).isJsonNull()) {
                    translatedText.append(translatedPart.get(0).getAsString());
                }
            }

            String detectedLanguage = "";
            if (response.size() > 2 && !response.get(2).isJsonNull()) {
                detectedLanguage = response.get(2).getAsString();
            }

            String resolvedTranslation = translatedText.length() > 0 ? translatedText.toString() : safeText;
            TranslationResponse translationResponse = TranslationResponse.success(safeText, resolvedTranslation, detectedLanguage, normalizedTargetLanguage);

            this.translationCache.put(cacheKey, new CachedTranslation(translationResponse));

            return translationResponse;
        } catch (Exception e) {
            LOGGER.error("Failed to translate text with Google Translate", e);
            return TranslationResponse.failure(safeText, normalizedTargetLanguage, "Failed to translate text with Google Translate.");
        }
    }

    public void clearCache() {
        this.translationCache.clear();
        this.languagesCache.clear();
    }

    private int getTimeoutMs() {
        return Math.max(1000, Emulator.getConfig().getInt("translate.google.timeout.ms", DEFAULT_TIMEOUT_MS));
    }

    private HttpsURLConnection openGet(String requestUrl) throws IOException {
        var connection = (HttpsURLConnection) URI.create(requestUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(this.getTimeoutMs());
        connection.setReadTimeout(this.getTimeoutMs());
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private JsonObject readJson(InputStream inputStream) throws IOException {
        try (var inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             var bufferedReader = new BufferedReader(inputStreamReader)) {
            return JsonParser.parseReader(bufferedReader).getAsJsonObject();
        }
    }

    private JsonArray readJsonArray(InputStream inputStream) throws IOException {
        try (var inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             var bufferedReader = new BufferedReader(inputStreamReader)) {
            return JsonParser.parseReader(bufferedReader).getAsJsonArray();
        }
    }

    private String readErrorMessage(HttpsURLConnection connection) {
        try {
            InputStream errorStream = connection.getErrorStream();

            if (errorStream == null) {
                return "Google Translate request failed with HTTP " + connection.getResponseCode() + '.';
            }

            try {
                JsonObject errorResponse = this.readJson(errorStream);

                if (errorResponse.has("error") && errorResponse.get("error").isJsonObject()) {
                    JsonObject errorObject = errorResponse.getAsJsonObject("error");

                    if (errorObject.has("message")) {
                        return errorObject.get("message").getAsString();
                    }
                }
            } catch (Exception ignored) {
                try (var inputStreamReader = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                     var bufferedReader = new BufferedReader(inputStreamReader)) {
                    var responseText = new StringBuilder();
                    String line;

                    while ((line = bufferedReader.readLine()) != null) {
                        responseText.append(line);
                    }

                    if (responseText.length() > 0) {
                        return responseText.toString();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Google Translate error response", e);
        }

        try {
            return "Google Translate request failed with HTTP " + connection.getResponseCode() + '.';
        } catch (IOException e) {
            return "Google Translate request failed.";
        }
    }

    private static String normalizeLanguageCode(String languageCode, String fallback) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return fallback;
        }

        String normalized = languageCode.trim().replace('_', '-');
        String[] split = normalized.split("-");

        if (split.length <= 1) {
            return normalized;
        }

        return split[0] + '-' + split[1].toUpperCase();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static List<SupportedLanguage> buildFreeSupportedLanguages() {
        var languages = new ArrayList<SupportedLanguage>();
        addLanguage(languages, "af", "Afrikaans");
        addLanguage(languages, "sq", "Albanian");
        addLanguage(languages, "am", "Amharic");
        addLanguage(languages, "ar", "Arabic");
        addLanguage(languages, "hy", "Armenian");
        addLanguage(languages, "az", "Azerbaijani");
        addLanguage(languages, "eu", "Basque");
        addLanguage(languages, "be", "Belarusian");
        addLanguage(languages, "bn", "Bengali");
        addLanguage(languages, "bs", "Bosnian");
        addLanguage(languages, "bg", "Bulgarian");
        addLanguage(languages, "ca", "Catalan");
        addLanguage(languages, "ceb", "Cebuano");
        addLanguage(languages, "ny", "Chichewa");
        addLanguage(languages, "zh-CN", "Chinese (Simplified)");
        addLanguage(languages, "zh-TW", "Chinese (Traditional)");
        addLanguage(languages, "co", "Corsican");
        addLanguage(languages, "hr", "Croatian");
        addLanguage(languages, "cs", "Czech");
        addLanguage(languages, "da", "Danish");
        addLanguage(languages, "nl", "Dutch");
        addLanguage(languages, "en", "English");
        addLanguage(languages, "eo", "Esperanto");
        addLanguage(languages, "et", "Estonian");
        addLanguage(languages, "tl", "Filipino");
        addLanguage(languages, "fi", "Finnish");
        addLanguage(languages, "fr", "French");
        addLanguage(languages, "fy", "Frisian");
        addLanguage(languages, "gl", "Galician");
        addLanguage(languages, "ka", "Georgian");
        addLanguage(languages, "de", "German");
        addLanguage(languages, "el", "Greek");
        addLanguage(languages, "gu", "Gujarati");
        addLanguage(languages, "ht", "Haitian Creole");
        addLanguage(languages, "ha", "Hausa");
        addLanguage(languages, "haw", "Hawaiian");
        addLanguage(languages, "iw", "Hebrew");
        addLanguage(languages, "hi", "Hindi");
        addLanguage(languages, "hmn", "Hmong");
        addLanguage(languages, "hu", "Hungarian");
        addLanguage(languages, "is", "Icelandic");
        addLanguage(languages, "ig", "Igbo");
        addLanguage(languages, "id", "Indonesian");
        addLanguage(languages, "ga", "Irish");
        addLanguage(languages, "it", "Italian");
        addLanguage(languages, "ja", "Japanese");
        addLanguage(languages, "jw", "Javanese");
        addLanguage(languages, "kn", "Kannada");
        addLanguage(languages, "kk", "Kazakh");
        addLanguage(languages, "km", "Khmer");
        addLanguage(languages, "rw", "Kinyarwanda");
        addLanguage(languages, "ko", "Korean");
        addLanguage(languages, "ku", "Kurdish");
        addLanguage(languages, "ky", "Kyrgyz");
        addLanguage(languages, "lo", "Lao");
        addLanguage(languages, "la", "Latin");
        addLanguage(languages, "lv", "Latvian");
        addLanguage(languages, "lt", "Lithuanian");
        addLanguage(languages, "lb", "Luxembourgish");
        addLanguage(languages, "mk", "Macedonian");
        addLanguage(languages, "mg", "Malagasy");
        addLanguage(languages, "ms", "Malay");
        addLanguage(languages, "ml", "Malayalam");
        addLanguage(languages, "mt", "Maltese");
        addLanguage(languages, "mi", "Maori");
        addLanguage(languages, "mr", "Marathi");
        addLanguage(languages, "mn", "Mongolian");
        addLanguage(languages, "my", "Myanmar");
        addLanguage(languages, "ne", "Nepali");
        addLanguage(languages, "no", "Norwegian");
        addLanguage(languages, "or", "Odia");
        addLanguage(languages, "ps", "Pashto");
        addLanguage(languages, "fa", "Persian");
        addLanguage(languages, "pl", "Polish");
        addLanguage(languages, "pt", "Portuguese");
        addLanguage(languages, "pa", "Punjabi");
        addLanguage(languages, "ro", "Romanian");
        addLanguage(languages, "ru", "Russian");
        addLanguage(languages, "sm", "Samoan");
        addLanguage(languages, "gd", "Scots");
        addLanguage(languages, "sr", "Serbian");
        addLanguage(languages, "st", "Sesotho");
        addLanguage(languages, "sn", "Shona");
        addLanguage(languages, "sd", "Sindhi");
        addLanguage(languages, "si", "Sinhala");
        addLanguage(languages, "sk", "Slovak");
        addLanguage(languages, "sl", "Slovenian");
        addLanguage(languages, "so", "Somali");
        addLanguage(languages, "es", "Spanish");
        addLanguage(languages, "su", "Sundanese");
        addLanguage(languages, "sw", "Swahili");
        addLanguage(languages, "sv", "Swedish");
        addLanguage(languages, "tg", "Tajik");
        addLanguage(languages, "ta", "Tamil");
        addLanguage(languages, "tt", "Tatar");
        addLanguage(languages, "te", "Telugu");
        addLanguage(languages, "th", "Thai");
        addLanguage(languages, "tr", "Turkish");
        addLanguage(languages, "tk", "Turkmen");
        addLanguage(languages, "uk", "Ukrainian");
        addLanguage(languages, "ur", "Urdu");
        addLanguage(languages, "ug", "Uyghur");
        addLanguage(languages, "uz", "Uzbek");
        addLanguage(languages, "vi", "Vietnamese");
        addLanguage(languages, "cy", "Welsh");
        addLanguage(languages, "xh", "Xhosa");
        addLanguage(languages, "yi", "Yiddish");
        addLanguage(languages, "yo", "Yoruba");
        addLanguage(languages, "zu", "Zulu");
        languages.sort(Comparator.comparing(SupportedLanguage::getName, String.CASE_INSENSITIVE_ORDER));
        return Collections.unmodifiableList(languages);
    }

    private static void addLanguage(List<SupportedLanguage> languages, String code, String name) {
        languages.add(new SupportedLanguage(code, name));
    }

    public static class SupportedLanguage {
        private final String code;
        private final String name;

        public SupportedLanguage(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() {
            return this.code;
        }

        public String getName() {
            return this.name;
        }
    }

    public static class SupportedLanguagesResponse {
        private final boolean success;
        private final String errorMessage;
        private final List<SupportedLanguage> languages;

        private SupportedLanguagesResponse(boolean success, String errorMessage, List<SupportedLanguage> languages) {
            this.success = success;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
            this.languages = languages == null ? Collections.emptyList() : languages;
        }

        public static SupportedLanguagesResponse success(List<SupportedLanguage> languages) {
            return new SupportedLanguagesResponse(true, "", languages);
        }

        public static SupportedLanguagesResponse failure(String errorMessage) {
            return new SupportedLanguagesResponse(false, errorMessage, Collections.emptyList());
        }

        public boolean isSuccess() {
            return this.success;
        }

        public String getErrorMessage() {
            return this.errorMessage;
        }

        public List<SupportedLanguage> getLanguages() {
            return this.languages;
        }
    }

    public static class TranslationResponse {
        private final boolean success;
        private final String errorMessage;
        private final String originalText;
        private final String translatedText;
        private final String detectedLanguage;
        private final String targetLanguage;

        private TranslationResponse(boolean success, String errorMessage, String originalText, String translatedText, String detectedLanguage, String targetLanguage) {
            this.success = success;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
            this.originalText = originalText == null ? "" : originalText;
            this.translatedText = translatedText == null ? "" : translatedText;
            this.detectedLanguage = detectedLanguage == null ? "" : detectedLanguage;
            this.targetLanguage = targetLanguage == null ? "" : targetLanguage;
        }

        public static TranslationResponse success(String originalText, String translatedText, String detectedLanguage, String targetLanguage) {
            return new TranslationResponse(true, "", originalText, translatedText, detectedLanguage, targetLanguage);
        }

        public static TranslationResponse failure(String originalText, String targetLanguage, String errorMessage) {
            return new TranslationResponse(false, errorMessage, originalText, originalText, "", targetLanguage);
        }

        public boolean isSuccess() {
            return this.success;
        }

        public String getErrorMessage() {
            return this.errorMessage;
        }

        public String getOriginalText() {
            return this.originalText;
        }

        public String getTranslatedText() {
            return this.translatedText;
        }

        public String getDetectedLanguage() {
            return this.detectedLanguage;
        }

        public String getTargetLanguage() {
            return this.targetLanguage;
        }
    }

    private static class CachedTranslation {
        private final long createdAt;
        private final TranslationResponse response;

        private CachedTranslation(TranslationResponse response) {
            this.createdAt = System.currentTimeMillis();
            this.response = response;
        }

        private boolean isExpired() {
            return (System.currentTimeMillis() - this.createdAt) > CACHE_TTL_MS;
        }
    }

    private static class CachedLanguages {
        private final long createdAt;
        private final List<SupportedLanguage> languages;

        private CachedLanguages(List<SupportedLanguage> languages) {
            this.createdAt = System.currentTimeMillis();
            this.languages = languages;
        }

        private boolean isExpired() {
            return (System.currentTimeMillis() - this.createdAt) > CACHE_TTL_MS;
        }
    }
}
