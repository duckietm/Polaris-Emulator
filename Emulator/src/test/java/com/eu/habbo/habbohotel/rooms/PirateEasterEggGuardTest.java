package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The "i am a pirate" easter egg consumes the chat message and returns early, so every guard it is
 * placed above is bypassable by simply repeating the trigger. These are structural ratchets:
 * {@link RoomChatManager#talk} needs a live {@link com.eu.habbo.Emulator} to exercise directly, so
 * the ordering and the one-song-per-Habbo gate are asserted against the source instead.
 */
class PirateEasterEggGuardTest {

    private static final Path CHAT_MANAGER =
            Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomChatManager.java");
    private static final Path PIRATE_RUNNABLE =
            Path.of("src/main/java/com/eu/habbo/threading/runnables/YouAreAPirate.java");

    @Test
    void easterEggIsEvaluatedAfterTheMuteAndFloodGuards() throws Exception {
        MethodDeclaration talk = talkMethod();
        String body = talk.getBody().orElseThrow().toString();

        int muteWhisper = body.indexOf("MutedWhisperComposer");
        int floodMute = body.indexOf("floodMuteHabbo");
        int easterEgg = body.indexOf("i am a pirate");

        assertTrue(muteWhisper >= 0, "expected the room mute guard to still exist in talk()");
        assertTrue(floodMute >= 0, "expected the flood protection guard to still exist in talk()");
        assertTrue(easterEgg >= 0, "expected the pirate easter egg to still exist in talk()");

        assertTrue(
                easterEgg > muteWhisper,
                "the pirate easter egg must be evaluated after the room mute guard, otherwise a"
                        + " room-muted Habbo can keep triggering it");
        assertTrue(
                easterEgg > floodMute,
                "the pirate easter egg must be evaluated after flood protection, otherwise the"
                        + " trigger is unrated and can be spammed without ever being flood muted");
    }

    @Test
    void onlyOneSongCanBeScheduledPerHabbo() throws Exception {
        MethodDeclaration talk = talkMethod();

        Optional<MethodCallExpr> gate = talk.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getNameAsString().equals("compareAndSet"))
                .filter(call -> call.getScope()
                        .map(scope -> scope.toString().endsWith("singingPirate"))
                        .orElse(false))
                .findFirst();

        assertTrue(
                gate.isPresent(),
                "talk() must gate YouAreAPirate behind singingPirate.compareAndSet(false, true);"
                        + " without it one client can stack unbounded singing tasks");
    }

    @Test
    void songReleasesTheGateOnEveryExitPath() throws Exception {
        CompilationUnit unit = StaticJavaParser.parse(Files.readString(PIRATE_RUNNABLE));
        MethodDeclaration run = unit.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals("run"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("YouAreAPirate.run() not found"));
        String body = run.getBody().orElseThrow().toString();

        List<String> exits = List.of(
                "getCurrentRoom", // singer left the room
                "allowTalk", // singer was muted mid-song
                "iamapirate.length"); // song finished

        for (String exit : exits) {
            assertTrue(body.contains(exit), "YouAreAPirate.run() must still handle the '" + exit + "' exit path");
        }

        long releases = unit.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getNameAsString().equals("set"))
                .filter(call -> call.getScope()
                        .map(scope -> scope.toString().endsWith("singingPirate"))
                        .orElse(false))
                .filter(call -> call.getArguments().size() == 1
                        && call.getArgument(0).toString().equals("false"))
                .count();

        assertTrue(
                releases >= 1,
                "YouAreAPirate must release singingPirate when the song ends, otherwise the Habbo"
                        + " can never trigger the easter egg again for the rest of the session");
    }

    @Test
    void easterEggStaysBehindItsConfigSwitch() throws Exception {
        MethodDeclaration talk = talkMethod();

        boolean gated = talk.findAll(StringLiteralExpr.class).stream()
                .anyMatch(literal -> literal.getValue().equals("easter_eggs.enabled"));

        assertTrue(gated, "the pirate easter egg must stay behind easter_eggs.enabled");
    }

    private static MethodDeclaration talkMethod() throws Exception {
        CompilationUnit unit = StaticJavaParser.parse(Files.readString(CHAT_MANAGER));

        return unit.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals("talk"))
                .filter(method -> method.getParameters().size() == 4)
                .findFirst()
                .orElseThrow(() -> new AssertionError("RoomChatManager.talk(4 args) not found"));
    }
}
