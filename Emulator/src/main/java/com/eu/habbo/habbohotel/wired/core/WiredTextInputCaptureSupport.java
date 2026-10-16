package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextInputVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboSaysKeyword;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WiredTextInputCaptureSupport {
    private static final int MATCH_CONTAINS = 0;
    private static final int MATCH_EXACT = 1;
    private static final int MATCH_ALL_WORDS = 2;
    // The official capture token is "#(name)"; ours, "#name#", keeps working for saved keywords.
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("#\\(([^()#]+)\\)|#([^#]+)#");
    // Cap the number of capture groups compiled into the fallback template
    // regex, which runs against every chat message in the room. Many groups cause
    // catastrophic backtracking (ReDoS): templates over the cap fall back to a
    // literal match instead of an unsafe backtracking pattern. Adjacent markers
    // never match at all (see compilePattern).
    private static final int MAX_TEMPLATE_PLACEHOLDERS = 8;
    private static final int PATTERN_CACHE_MAX = 256;
    private static final Map<String, TemplatePattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private WiredTextInputCaptureSupport() {}

    public static CaptureResult resolve(WiredStack stack, WiredEvent event) {
        if (stack == null || event == null || !(stack.triggerItem() instanceof WiredTriggerHabboSaysKeyword)) {
            return CaptureResult.noMatch();
        }

        WiredTriggerHabboSaysKeyword trigger = (WiredTriggerHabboSaysKeyword) stack.triggerItem();
        Room room = event.getRoom();
        RoomUnit actor = event.getActor().orElse(null);
        String text = event.getText().orElse(null);

        if (room == null || actor == null || text == null) {
            return CaptureResult.noMatch();
        }

        List<WiredExtraTextInputVariable> capturers = getCapturers(room, trigger);
        if (capturers.isEmpty()) {
            return trigger.matches(stack.triggerItem(), event)
                    ? CaptureResult.matched(new LinkedHashMap<>())
                    : CaptureResult.noMatch();
        }

        if (trigger.isOwnerOnly()) {
            Habbo habbo = room.getHabbo(actor);
            if (habbo == null || room.getOwnerId() != habbo.getHabboInfo().getId()) {
                return CaptureResult.noMatch();
            }
        }

        LinkedHashMap<String, WiredExtraTextInputVariable> capturersByName = new LinkedHashMap<>();
        for (WiredExtraTextInputVariable capturer : capturers) {
            if (capturer == null
                    || capturer.getCapturerName() == null
                    || capturer.getCapturerName().trim().isEmpty()) {
                continue;
            }

            capturersByName.put(capturer.getCapturerName().trim().toLowerCase(), capturer);
        }

        if (capturersByName.isEmpty()) {
            return trigger.matches(stack.triggerItem(), event)
                    ? CaptureResult.matched(new LinkedHashMap<>())
                    : CaptureResult.noMatch();
        }

        MatchResult matchResult = matchTemplate(trigger, text, capturersByName, room);
        if (!matchResult.matches) {
            if (WiredManager.isDebugEnabled()) {
                WiredManager.debug(
                        "[TextCapture] NO_MATCH room={} triggerId={} mode={} key='{}' text='{}' len={}",
                        room.getId(),
                        stack.triggerItem().getId(),
                        trigger.getMatchMode(),
                        safeForLog(trigger.getKey()),
                        safeForLog(text),
                        (text != null ? text.length() : 0));
            }
            return CaptureResult.noMatch();
        }

        LinkedHashMap<Integer, Integer> capturedValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> capture : matchResult.captures.entrySet()) {
            WiredExtraTextInputVariable capturer = capturersByName.get(capture.getKey());
            if (capturer == null) {
                continue;
            }

            Integer resolvedValue = capturer.resolveCapturedValue(room, capture.getValue());
            if (resolvedValue == null) {
                if (WiredManager.isDebugEnabled()) {
                    WiredManager.debug(
                            "[TextCapture] RESOLVE_FAIL room={} triggerId={} capturer='{}' raw='{}' rawLen={}",
                            room.getId(),
                            stack.triggerItem().getId(),
                            capture.getKey(),
                            safeForLog(capture.getValue()),
                            (capture.getValue() != null ? capture.getValue().length() : 0));
                }
                return CaptureResult.noMatch();
            }

            capturedValues.put(capturer.getVariableItemId(), resolvedValue);
        }

        if (WiredManager.isDebugEnabled()) {
            WiredManager.debug(
                    "[TextCapture] MATCH_OK room={} triggerId={} captures={} textLen={}",
                    room.getId(),
                    stack.triggerItem().getId(),
                    capturedValues.size(),
                    (text != null ? text.length() : 0));
        }

        return CaptureResult.matched(capturedValues);
    }

    private static List<WiredExtraTextInputVariable> getCapturers(Room room, WiredTriggerHabboSaysKeyword trigger) {
        List<WiredExtraTextInputVariable> capturers = new ArrayList<>();

        if (room == null || trigger == null || room.getRoomSpecialTypes() == null) {
            return capturers;
        }

        Collection<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(trigger.getX(), trigger.getY());
        if (extras == null || extras.isEmpty()) {
            return capturers;
        }

        for (InteractionWiredExtra extra : WiredExecutionOrderUtil.sort(extras)) {
            if (extra instanceof WiredExtraTextInputVariable) {
                capturers.add((WiredExtraTextInputVariable) extra);
            }
        }

        return capturers;
    }

    private static MatchResult matchTemplate(
            WiredTriggerHabboSaysKeyword trigger,
            String rawText,
            Map<String, WiredExtraTextInputVariable> capturersByName,
            Room room) {
        String text = rawText != null ? rawText : "";
        String normalizedText = text.trim();
        String template = trigger.getKey() != null ? trigger.getKey().trim() : "";

        if (trigger.getMatchMode() == MATCH_ALL_WORDS && template.isEmpty()) {
            if (capturersByName.size() != 1 || normalizedText.isEmpty()) {
                return MatchResult.noMatch();
            }

            String placeholderName = capturersByName.keySet().iterator().next();
            LinkedHashMap<String, String> captures = new LinkedHashMap<>();
            captures.put(placeholderName, text);
            return MatchResult.matched(captures);
        }

        TemplatePattern pattern = buildPattern(template);
        if (pattern == null || pattern == TemplatePattern.NEVER) {
            return MatchResult.noMatch();
        }

        String matchText = pattern.placeholderNames.isEmpty() ? normalizedText : text;
        Matcher matcher = pattern.pattern.matcher(matchText);
        boolean matches = (trigger.getMatchMode() == MATCH_CONTAINS) ? matcher.find() : matcher.matches();
        if (!matches) {
            return MatchResult.noMatch();
        }

        LinkedHashMap<String, String> captures = new LinkedHashMap<>();
        for (int index = 0; index < pattern.placeholderNames.size(); index++) {
            String placeholderName = pattern.placeholderNames.get(index);
            // A marker without a capturer of its own stops the trigger, as in Habbo.
            if (!capturersByName.containsKey(placeholderName)) {
                return MatchResult.noMatch();
            }

            String capturedValue = matcher.group(index + 1);
            captures.put(placeholderName, normalizeCapturedValue(capturedValue));
        }

        return MatchResult.matched(captures);
    }

    private static String normalizeCapturedValue(String value) {
        return value != null ? value : "";
    }

    private static String safeForLog(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.replace("\r", "\\r").replace("\n", "\\n").replace("\u00A0", "⍽");

        if (normalized.length() > 180) {
            return normalized.substring(0, 180) + "...(" + normalized.length() + ")";
        }

        return normalized;
    }

    private static TemplatePattern buildPattern(String template) {
        if (template == null || template.isEmpty()) {
            return null;
        }

        TemplatePattern cached = PATTERN_CACHE.get(template);
        if (cached != null) {
            return cached;
        }

        TemplatePattern built = compilePattern(template);
        if (PATTERN_CACHE.size() >= PATTERN_CACHE_MAX) {
            PATTERN_CACHE.clear();
        }
        PATTERN_CACHE.put(template, built);
        return built;
    }

    static Pattern templatePattern(String template) {
        TemplatePattern pattern = buildPattern(template);
        return (pattern != null) ? pattern.pattern : null;
    }

    private static String placeholderName(Matcher matcher) {
        String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return name != null ? name.trim().toLowerCase() : "";
    }

    private static TemplatePattern compilePattern(String template) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        List<String> literals = new ArrayList<>();
        List<String> placeholderNames = new ArrayList<>();
        int cursor = 0;
        boolean unsafe = false;

        while (matcher.find()) {
            // Two markers with nothing between them never match in Habbo, with or without
            // a literal before them (#a##b#, qr:#(a)#(b), #(a)#(b)).
            if (matcher.start() == cursor && !placeholderNames.isEmpty()) {
                return TemplatePattern.NEVER;
            }

            literals.add(template.substring(cursor, matcher.start()));
            String placeholderName = placeholderName(matcher);
            placeholderNames.add(placeholderName);
            cursor = matcher.end();

            if (placeholderNames.size() > MAX_TEMPLATE_PLACEHOLDERS) {
                unsafe = true;
                break;
            }
        }

        if (unsafe || placeholderNames.isEmpty()) {
            // Literal match, no captures — safe and bounded regardless of input.
            return new TemplatePattern(
                    Pattern.compile(Pattern.quote(template), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    new ArrayList<>());
        }

        literals.add(template.substring(cursor));

        // Each value but the last ends at the first following separator, inside an atomic group,
        // so a line full of separators cannot make the matcher try every split (that took seconds).
        StringBuilder regex = new StringBuilder(Pattern.quote(literals.get(0)));
        int last = placeholderNames.size() - 1;
        for (int index = 0; index < last; index++) {
            regex.append("(?>(.+?)")
                    .append(Pattern.quote(literals.get(index + 1)))
                    .append(')');
        }
        regex.append("(.+)").append(Pattern.quote(literals.get(last + 1)));

        return new TemplatePattern(
                Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), placeholderNames);
    }

    public static void applyToContext(WiredContext ctx, Room room, CaptureResult captureResult) {
        if (ctx == null
                || room == null
                || captureResult == null
                || !captureResult.matches
                || captureResult.capturedValues.isEmpty()) {
            return;
        }

        ctx.forkContextVariables();

        for (Map.Entry<Integer, Integer> entry : captureResult.capturedValues.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getKey() <= 0) {
                continue;
            }

            if (!WiredContextVariableSupport.updateVariableValue(ctx, room, entry.getKey(), entry.getValue())) {
                WiredContextVariableSupport.assignVariable(ctx, room, entry.getKey(), entry.getValue(), false);
            }
        }
    }

    public static final class CaptureResult {
        private final boolean matches;
        private final LinkedHashMap<Integer, Integer> capturedValues;

        private CaptureResult(boolean matches, LinkedHashMap<Integer, Integer> capturedValues) {
            this.matches = matches;
            this.capturedValues = capturedValues;
        }

        public boolean matches() {
            return this.matches;
        }

        public static CaptureResult matched(LinkedHashMap<Integer, Integer> capturedValues) {
            return new CaptureResult(true, capturedValues);
        }

        public static CaptureResult noMatch() {
            return new CaptureResult(false, new LinkedHashMap<>());
        }
    }

    private static final class MatchResult {
        private final boolean matches;
        private final LinkedHashMap<String, String> captures;

        private MatchResult(boolean matches, LinkedHashMap<String, String> captures) {
            this.matches = matches;
            this.captures = captures;
        }

        private static MatchResult matched(LinkedHashMap<String, String> captures) {
            return new MatchResult(true, captures);
        }

        private static MatchResult noMatch() {
            return new MatchResult(false, new LinkedHashMap<>());
        }
    }

    private static final class TemplatePattern {
        /** A template that cannot match any line. */
        private static final TemplatePattern NEVER = new TemplatePattern(Pattern.compile("(?!)"), new ArrayList<>());

        private final Pattern pattern;
        private final List<String> placeholderNames;

        private TemplatePattern(Pattern pattern, List<String> placeholderNames) {
            this.pattern = pattern;
            this.placeholderNames = placeholderNames;
        }
    }
}
