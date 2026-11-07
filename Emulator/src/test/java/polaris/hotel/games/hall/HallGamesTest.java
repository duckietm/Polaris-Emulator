package polaris.hotel.games.hall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class HallGamesTest {

    private static final long NOW = 1_000_000L;

    private static JsonObject at(int x, int y) {
        JsonObject move = new JsonObject();

        move.addProperty("x", x);
        move.addProperty("y", y);
        return move;
    }

    private static JsonObject chess(String from, String to) {
        JsonObject move = new JsonObject();

        move.addProperty("from", square(from));
        move.addProperty("to", square(to));
        return move;
    }

    private static int square(String name) {
        return (8 - (name.charAt(1) - '0')) * 8 + (name.charAt(0) - 'a');
    }

    // ---- Tic-tac-toe

    @Test
    void fiveInARowWins() {
        TicTacToe game = new TicTacToe();

        game.start(2, new Random(1));

        for (int i = 0; i < 4; i++) {
            game.play(0, at(5 + i, 5));
            game.play(1, at(5 + i, 8));
        }

        assertThrows(HallException.class, () -> game.play(1, at(0, 0)));
        assertThrows(HallException.class, () -> game.play(0, at(5, 5)));

        game.play(0, at(9, 5));

        assertTrue(game.finished());
        assertEquals(List.of(0), game.winners());
        assertEquals(5, game.view(0).getAsJsonArray("line").size());
    }

    @Test
    void theTicTacToeRobotBlocksFour() {
        TicTacToe game = new TicTacToe();

        game.start(2, new Random(1));

        for (int i = 0; i < 3; i++) {
            game.play(0, at(3 + i, 3));
            game.play(1, at(10 + i, 15));
        }

        game.play(0, at(6, 3));
        game.bot(1, new Random(2));

        assertTrue(game.cell(2, 3) == 2 || game.cell(7, 3) == 2);
    }

    // ---- Four in a row

    @Test
    void fourDiscsInAColumnWin() {
        ConnectFour game = new ConnectFour();

        game.start(2, new Random(1));

        for (int i = 0; i < 3; i++) {
            game.play(0, at(0, 0));
            game.play(1, at(1, 0));
        }

        game.play(0, at(0, 0));

        assertTrue(game.finished());
        assertEquals(List.of(0), game.winners());
    }

    @Test
    void aFullColumnRefusesDiscs() {
        ConnectFour game = new ConnectFour();

        game.start(2, new Random(1));

        for (int i = 0; i < 6; i++) game.play(i % 2, at(3, 0));

        assertEquals(
                "bad_move",
                assertThrows(HallException.class, () -> game.play(0, at(3, 0))).code());
    }

    @Test
    void theFourInARowRobotTakesAWin() {
        ConnectFour game = new ConnectFour();

        game.start(2, new Random(1));
        game.play(0, at(0, 0));
        game.play(1, at(3, 0));
        game.play(0, at(0, 0));
        game.play(1, at(4, 0));
        game.play(0, at(6, 0));
        game.play(1, at(5, 0));
        game.play(0, at(6, 0));
        game.bot(1, new Random(3));

        assertTrue(game.finished());
        assertEquals(List.of(1), game.winners());
    }

    // ---- Battleships

    @Test
    void aRandomFleetIsValid() {
        for (int seed = 0; seed < 50; seed++) {
            assertTrue(Battleships.valid(Battleships.randomFleet(new Random(seed))));
        }
    }

    @Test
    void shipsMayNotTouch() {
        JsonObject move = new JsonObject();
        JsonArray ships = new JsonArray();
        int[][] layout = {
            {0, 0, 5},
            {0, 2, 4},
            {0, 3, 4},
            {0, 6, 3},
            {0, 8, 3},
            {0, 10, 3},
            {7, 0, 2},
            {7, 2, 2},
            {7, 4, 2},
            {7, 6, 2}
        };

        for (int[] ship : layout) {
            JsonObject entry = new JsonObject();

            entry.addProperty("x", ship[0]);
            entry.addProperty("y", ship[1]);
            entry.addProperty("length", ship[2]);
            entry.addProperty("vertical", false);
            ships.add(entry);
        }

        move.add("ships", ships);

        Battleships game = new Battleships();

        game.start(2, new Random(1));

        assertEquals(
                "bad_fleet",
                assertThrows(HallException.class, () -> game.play(0, move)).code());
    }

    @Test
    void aHitShootsAgainAMissPasses() {
        Battleships game = new Battleships();
        JsonObject random = new JsonObject();

        random.addProperty("random", true);
        game.start(2, new Random(1));
        game.play(0, random);
        assertTrue(game.waitingFor(1));
        game.play(1, random);
        assertFalse(game.placing());

        JsonObject view = game.view(0);
        JsonObject theirSea = view.getAsJsonArray("seas").get(1).getAsJsonObject();

        assertEquals(0, theirSea.getAsJsonArray("ships").size());
        assertEquals(
                10,
                game.view(1)
                        .getAsJsonArray("seas")
                        .get(1)
                        .getAsJsonObject()
                        .getAsJsonArray("ships")
                        .size());

        // Shoot every cell of seat 1's sea in order: the turn only passes on a miss.
        for (int y = 0; y < Battleships.SIZE && !game.finished(); y++) {
            for (int x = 0; x < Battleships.SIZE && !game.finished(); x++) {
                while (!game.finished() && game.waitingFor(1)) game.bot(1, new Random(x + y));

                if (!game.finished()) game.play(0, at(x, y));
            }
        }

        assertTrue(game.finished());
        assertEquals(1, game.winners().size());
    }

    // ---- Chess

    @Test
    void foolsMate() {
        Chess game = new Chess();

        game.start(2, new Random(1));
        game.play(0, chess("f2", "f3"));
        game.play(1, chess("e7", "e5"));
        game.play(0, chess("g2", "g4"));

        assertThrows(HallException.class, () -> game.play(1, chess("d8", "d4")));

        game.play(1, chess("d8", "h4"));

        assertTrue(game.finished());
        assertEquals(List.of(1), game.winners());
        assertEquals("checkmate", game.view(0).get("ending").getAsString());
    }

    @Test
    void castlingEnPassantAndPromotion() {
        Chess game = new Chess();

        game.start(2, new Random(1));
        game.play(0, chess("e2", "e4"));
        game.play(1, chess("a7", "a6"));
        game.play(0, chess("e4", "e5"));
        game.play(1, chess("d7", "d5"));
        game.play(0, chess("e5", "d6"));

        assertEquals(0, game.at(square("d5")));
        assertEquals(Chess.PAWN, game.at(square("d6")));

        game.play(1, chess("a6", "a5"));
        game.play(0, chess("g1", "f3"));
        game.play(1, chess("a5", "a4"));
        game.play(0, chess("f1", "e2"));
        game.play(1, chess("a4", "a3"));
        game.play(0, chess("e1", "g1"));

        assertEquals(Chess.KING, game.at(square("g1")));
        assertEquals(Chess.ROOK, game.at(square("f1")));

        game.play(1, chess("a3", "b2"));
        game.play(0, chess("d6", "c7"));
        game.play(1, chess("b2", "a1"));

        assertEquals(-Chess.QUEEN, game.at(square("a1")));
    }

    @Test
    void stalemateIsADraw() {
        Chess game = new Chess();

        game.start(2, new Random(1));
        game.setUp(
                "k......." + "........" + ".Q......" + "........" + "........" + "........" + "........" + "......K.",
                0);
        game.play(0, chess("b6", "c7"));

        assertTrue(game.finished());
        assertEquals(List.of(), game.winners());
        assertEquals("stalemate", game.view(0).get("ending").getAsString());
    }

    @Test
    void theChessRobotTakesAFreeQueen() {
        Chess game = new Chess();

        game.start(2, new Random(1));
        game.play(0, chess("e2", "e4"));
        game.play(1, chess("d7", "d5"));
        game.play(0, chess("d1", "h5"));
        game.play(1, chess("g8", "f6"));
        game.play(0, chess("h5", "d5"));

        game.bot(1, new Random(4));

        assertTrue(game.at(square("d5")) < 0);
    }

    // ---- Poker

    @Test
    void pokerHandsInOrder() {
        long high = Poker.value(List.of("h2", "d5", "c9", "s11", "h13"));
        long pair = Poker.value(List.of("h2", "d2", "c9", "s11", "h13"));
        long twoPairs = Poker.value(List.of("h2", "d2", "c9", "s9", "h13"));
        long three = Poker.value(List.of("h2", "d2", "c2", "s9", "h13"));
        long wheel = Poker.value(List.of("h1", "d2", "c3", "s4", "h5"));
        long straight = Poker.value(List.of("h6", "d2", "c3", "s4", "h5"));
        long flush = Poker.value(List.of("h2", "h5", "h9", "h11", "h13"));
        long full = Poker.value(List.of("h2", "d2", "c2", "s9", "h9"));
        long four = Poker.value(List.of("h2", "d2", "c2", "s2", "h9"));
        long royal = Poker.value(List.of("s10", "s11", "s12", "s13", "s1"));
        long five = Poker.value(List.of("h2", "d2", "c2", "s2", Poker.JOKER));

        assertTrue(high < pair && pair < twoPairs && twoPairs < three && three < wheel && wheel < straight);
        assertTrue(straight < flush && flush < full && full < four && four < royal && royal < five);
        assertEquals(Poker.ROYAL_FLUSH, Poker.kind(List.of("s10", "s11", "s12", Poker.JOKER, "s1")));
        assertTrue(Poker.value(List.of("h13", "d13", "c9", "s4", "h2"))
                > Poker.value(List.of("h12", "d12", "c1", "s9", "h7")));
    }

    @Test
    void pokerShowsTheHandsOnceEverybodyChanged() {
        Poker game = new Poker();

        game.start(3, new Random(7));

        assertEquals(
                "",
                game.view(0)
                        .getAsJsonArray("hands")
                        .get(1)
                        .getAsJsonObject()
                        .getAsJsonArray("cards")
                        .get(0)
                        .getAsString());

        JsonObject change = new JsonObject();
        JsonArray cards = new JsonArray();

        cards.add(0);
        cards.add(4);
        change.add("change", cards);
        game.play(0, change);
        game.bot(1, new Random(1));

        assertFalse(game.finished());

        game.bot(2, new Random(2));

        assertTrue(game.finished());
        assertFalse(game.winners().isEmpty());
        assertFalse(game.view(0)
                .getAsJsonArray("hands")
                .get(1)
                .getAsJsonObject()
                .getAsJsonArray("cards")
                .get(0)
                .getAsString()
                .isEmpty());
    }

    // ---- The snail race

    @Test
    void snailsRaceToTheLineAndCheeringHelps() {
        SnailRace race = new SnailRace();

        race.start(3, new Random(9));
        race.tick(NOW, new Random(1));

        assertFalse(race.waitingFor(0));
        assertThrows(HallException.class, () -> race.play(0, null));

        long now = NOW + SnailRace.COUNTDOWN_MS;

        race.tick(now, null);
        assertTrue(race.waitingFor(0));
        race.play(0, null);
        assertFalse(race.waitingFor(0));

        for (int i = 0; i < 900 && !race.finished(); i++) {
            now += 100;
            race.tick(now, null);

            if (race.waitingFor(0)) race.play(0, null);
        }

        assertTrue(race.finished());
        assertEquals(1, race.winners().size());
        assertTrue(race.snail(0).place >= 1 && race.snail(0).place <= 3);
        assertTrue(race.snail(0).x >= SnailRace.LENGTH);
    }

    // ---- The quiz

    @Test
    void theQuizBankIsComplete() {
        assertTrue(QuizBank.QUESTIONS.size() >= 50);

        for (Quiz.Question question : QuizBank.QUESTIONS) {
            assertEquals(4, question.answers().size(), question.text());
            assertTrue(question.right() >= 0 && question.right() < 4, question.text());
        }
    }

    @Test
    void aRightQuickAnswerScoresMore() {
        Quiz quiz = new Quiz(QuizBank.QUESTIONS);

        quiz.start(2, new Random(3));
        quiz.tick(NOW, null);
        quiz.tick(NOW + Quiz.INTRO_MS, null);

        assertTrue(quiz.waitingFor(0));

        int right = -1;

        // Seat 1 is a robot that knows: it plays right three times in five; find the right answer from its view.
        quiz.tick(NOW + Quiz.INTRO_MS + 500, null);

        JsonObject move = new JsonObject();

        for (int answer = 0; answer < 4; answer++) {
            Quiz probe = new Quiz(QuizBank.QUESTIONS);

            probe.start(2, new Random(3));
            probe.tick(NOW, null);
            probe.tick(NOW + Quiz.INTRO_MS, null);
            move.addProperty("answer", answer);
            probe.play(0, move);
            probe.play(1, move);
            probe.tick(NOW + Quiz.INTRO_MS + 100, null);

            if (probe.score(0) > 0) right = answer;
        }

        assertTrue(right >= 0);

        move.addProperty("answer", right);
        quiz.play(0, move);
        quiz.tick(NOW + Quiz.INTRO_MS + 10_000, null);
        quiz.play(1, move);
        quiz.tick(NOW + Quiz.INTRO_MS + 10_100, null);

        assertTrue(quiz.score(0) > quiz.score(1));
        assertTrue(quiz.score(1) >= 500);
    }

    // ---- Tetris

    @Test
    void tetrisSendsRubbishAndTheLastStandingWins() {
        TetrisDuel duel = new TetrisDuel();

        duel.start(3, new Random(2));
        duel.tick(NOW, null);

        JsonObject move = new JsonObject();

        move.addProperty("cleared", 4);
        assertThrows(HallException.class, () -> duel.play(0, move));

        duel.tick(NOW + TetrisDuel.COUNTDOWN_MS, null);
        duel.play(0, move);

        assertEquals(4, duel.board(1).received + duel.board(2).received);
        assertEquals(2, duel.board(1).received);

        JsonObject out = new JsonObject();

        out.addProperty("out", true);
        duel.play(1, out);
        assertFalse(duel.finished());
        duel.play(2, out);

        assertTrue(duel.finished());
        assertEquals(List.of(0), duel.winners());
        assertEquals(3, duel.board(1).place);
        assertEquals(2, duel.board(2).place);
    }

    // ---- The diving board

    @Test
    void divingMarksEachDiveAndTheBestTotalWins() {
        Diving diving = new Diving();
        Random random = new Random(5);

        diving.start(3, random);
        diving.tick(NOW, random);
        diving.tick(NOW + Diving.INTRO_MS, random);

        assertTrue(diving.waitingFor(0));
        assertFalse(diving.waitingFor(1));

        JsonObject bad = new JsonObject();

        bad.addProperty("keys", "aadd!!");
        assertThrows(HallException.class, () -> diving.play(0, bad));

        JsonObject dive = new JsonObject();

        dive.addProperty("keys", "0adadadadad aaaassswwee0000");
        diving.play(0, dive);
        assertEquals("SHOW", diving.phaseName());

        long at = NOW + Diving.INTRO_MS + 60_000L;

        diving.tick(at, random);
        assertEquals("VOTE", diving.phaseName());
        assertFalse(diving.waitingFor(0));

        JsonObject eight = new JsonObject();
        JsonObject six = new JsonObject();

        eight.addProperty("mark", 8);
        six.addProperty("mark", 6);
        diving.play(1, eight);
        diving.play(2, six);
        diving.tick(at, random);

        assertEquals("SCORE", diving.phaseName());
        assertEquals(7.0, diving.total(0), 0.001);

        diving.tick(at + Diving.SCORE_MS, random);
        assertTrue(diving.waitingFor(1));
    }

    @Test
    void divingRobotsPlayAWholeContest() {
        Diving diving = new Diving();
        Random random = new Random(9);
        long now = NOW;

        diving.start(4, random);

        for (int step = 0; step < 4000 && !diving.finished(); step++) {
            diving.tick(now, random);

            for (int seat = 0; seat < 4; seat++) {
                if (diving.waitingFor(seat)) diving.bot(seat, random);
            }

            now += 500;
        }

        assertTrue(diving.finished());
        assertFalse(diving.winners().isEmpty());
        assertTrue(Diving.robotMark(Diving.robotKeys(random, 3), random) >= 0);
    }

    // ---- HabboKart

    @Test
    void kartRobotsGoRoundAndThePlayerWhoArrivesFirstWins() {
        KartRace race = new KartRace();
        Random random = new Random(3);

        race.start(3, random);
        race.tick(NOW, random);
        race.bot(1, random);
        race.bot(2, random);

        JsonObject early = new JsonObject();

        early.addProperty("x", 1.0);
        early.addProperty("y", 1.0);
        early.addProperty("progress", 1.0);
        assertThrows(HallException.class, () -> race.play(0, early));

        long now = NOW + KartRace.COUNTDOWN_MS;

        race.tick(now, random);
        assertTrue(race.waitingFor(0));
        assertFalse(race.waitingFor(1));

        // The player drives round, a few blocks at a time, faster than the robots.
        double progress = -0.35;

        while (!race.finished() && progress < KartRace.LAPS * KartRace.TRACK.length + 1) {
            now += 400;
            progress += 1.2;
            race.tick(now, random);

            if (race.finished()) break;

            double[] at = KartRace.centreAt(progress, 0);
            JsonObject move = new JsonObject();

            move.addProperty("x", at[0]);
            move.addProperty("y", at[1]);
            move.addProperty("progress", progress);
            race.play(0, move);
        }

        assertTrue(race.finished());
        assertEquals(List.of(0), race.winners());
        assertEquals(1, race.car(0).place);
        assertTrue(race.car(1).progress > 5);
    }

    // ---- Copy Writer (Lost Monkey)

    @Test
    void copyWriterSendsTheWrongOneToSleepAndTheLastAwakeWins() {
        CopyWriter game = new CopyWriter();
        Random random = new Random(8);

        game.start(3, random);
        game.tick(NOW, random);
        game.tick(NOW + CopyWriter.INTRO_MS, random);

        assertEquals("SHOW", game.phaseName());
        assertEquals(CopyWriter.FIRST_LENGTH, game.row().length());
        assertFalse(game.waitingFor(0));

        long now = NOW + CopyWriter.INTRO_MS + game.showMs();

        game.tick(now, random);
        assertEquals("ANSWER", game.phaseName());

        JsonObject right = new JsonObject();
        JsonObject wrong = new JsonObject();

        right.addProperty("keys", game.row());
        wrong.addProperty("keys", game.row().charAt(0) == 'A' ? "B" : "A");
        game.play(0, right);
        game.play(1, right);
        game.play(2, wrong);
        game.tick(now, random);

        assertEquals("RESULT", game.phaseName());
        assertFalse(game.waitingFor(2));

        now += CopyWriter.RESULT_MS;
        game.tick(now, random);
        assertEquals(CopyWriter.FIRST_LENGTH + 1, game.row().length());

        now += game.showMs();
        game.tick(now, random);

        JsonObject longer = new JsonObject();

        longer.addProperty("keys", game.row());
        game.play(0, longer);
        game.play(1, wrong);
        game.tick(now, random);

        assertTrue(game.finished());
        assertEquals(List.of(0), game.winners());
    }

    @Test
    void copyWriterRobotsPlayUntilTheEnd() {
        CopyWriter game = new CopyWriter();
        Random random = new Random(4);
        long now = NOW;

        game.start(4, random);

        for (int step = 0; step < 5000 && !game.finished(); step++) {
            game.tick(now, random);

            for (int seat = 0; seat < 4; seat++) {
                if (game.waitingFor(seat)) game.bot(seat, random);
            }

            now += 400;
        }

        assertTrue(game.finished());
        assertFalse(game.winners().isEmpty());
    }

    // ---- The tables

    @Test
    void robotsPlayWholeGamesOfEveryKind() {
        for (HallGames.Kind kind : HallGames.KINDS) {
            for (int round = 0; round < 3; round++) {
                HallTable table = new HallTable(1, kind, kind.seats(), true, 60_000L, new Random(round * 31 + 7));

                table.join(1, "P1", "", "M", NOW);

                while (table.seats().size() < kind.seats()) table.addBot(1, NOW);

                table.start(1, NOW);

                long now = NOW;

                // The person plays as a robot would, out of time each move (at most twice, then a robot steps in).
                for (int step = 0; step < 4000 && table.state() == HallTable.State.PLAYING; step++) {
                    now += 2_000L;

                    if (table.game().waitingFor(0)) table.game().bot(0, new Random(step));

                    table.advance(now);
                }

                assertEquals(HallTable.State.FINISHED, table.state(), kind.name());
                assertTrue(table.takeRecord());
            }
        }
    }

    @Test
    void aPlayerOutOfTimeLosesAfterThreeTimes() {
        HallTable table = new HallTable(1, HallGames.kind("connect4"), 2, true, 10_000L, new Random(1));

        table.join(1, "P1", "", "M", NOW);
        table.addBot(1, NOW);
        table.start(1, NOW);

        long now = NOW;

        for (int i = 0; i < 400 && table.state() == HallTable.State.PLAYING; i++) {
            now += 1_000L;
            table.advance(now);
        }

        assertEquals(HallTable.State.FINISHED, table.state());
        assertEquals(1, table.seats().get(1).wins() + table.seats().get(0).wins());
    }

    @Test
    void leavingAGameLosesIt() {
        HallTable table = new HallTable(1, HallGames.kind("chess"), 2, true, 60_000L, new Random(1));

        table.join(1, "P1", "", "M", NOW);
        table.join(2, "P2", "", "F", NOW);
        table.start(1, NOW);
        table.leave(2, NOW);

        assertEquals(1, table.results().get(0)[0]);
        assertEquals(1, table.results().get(0)[1]);
        assertEquals(HallTable.State.WAITING, table.state());
        assertEquals(1, table.seats().size());
    }
}
