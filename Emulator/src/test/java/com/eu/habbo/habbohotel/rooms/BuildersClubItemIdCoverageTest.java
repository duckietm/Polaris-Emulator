package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every item id that crosses the protocol boundary goes through {@link BuildersClubItemIds}: composers write
 * {@code toWire}, handlers read through {@code fromWire}. A Builders Club furni the client knows by an id of the
 * official band would otherwise be addressed by its database id in one packet and by its band id in the next.
 *
 * <p>The scan is source based (no type solver): an item id is a {@code getId()} on a value declared as {@code
 * HabboItem} or one of its subclasses, {@code getTeleportTargetId()}, or an int named like an item, furni, object or
 * stuff id. A finding that is not an in-room furni reference goes on {@link #REVIEWED} with its reason.
 */
class BuildersClubItemIdCoverageTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final String BASE = "com/eu/habbo/";

    /** Where packets are written. */
    private static final List<String> OUTGOING_SCOPE = List.of(
            BASE + "messages/outgoing/",
            BASE + "habbohotel/",
            BASE + "networking/gameserver/wired/",
            BASE + "messages/incoming/");

    /** Where client packets are read. */
    private static final List<String> INCOMING_SCOPE = List.of(BASE + "messages/incoming/");

    private static final Pattern ITEM_ID_NAME =
            Pattern.compile("(?i)(.*(item|furni|object|stuff|teleport|target|wired|box|toner|chest|gate|lock|entity"
                    + "|holder|anchor|source|roller)_?id|(get)?(item|furni|object|stuff)_?id|furniture_?id)");
    /** A bare {@code id} inside a class whose name says it is about furni. */
    private static final Pattern FURNI_CLASS = Pattern.compile("(?i).*(furni|item|wall|stuff).*");

    private static final Pattern ITEM_IDS_NAME = Pattern.compile("(?i).*(item|furni|object|stuff)_?ids\\d?");
    /** Names that look like item ids but are not furni instances. */
    private static final Pattern NOT_A_FURNI_ID = Pattern.compile("(?i).*(base|catalog|definition|offer|page|sprite"
            + "|club|reward|prize|product|class|type|limited|clothing|song|poll|bot|pet|user|room|guild|badge|group"
            + "|achievement|quest|deal|bundle|variable|wiredvariable|holder|network|label|playlist|video|effect)"
            + "_?(item)?_?ids?\\d?|.*itemdefinitionid|.*baseitemid|.*catalogitemid");

    private static final Set<String> LOOKUPS = Set.of(
            "getHabboItem",
            "getItem",
            "getFloorItem",
            "getWallItem",
            "getSpecialItem",
            "getEffect",
            "getTrigger",
            "getCondition",
            "getExtra",
            "getSelector",
            "getItemIncarnation");

    /**
     * Reviewed findings that are not in-room furni references: path, then the flagged expression. Each value says
     * why it stays a database id (or why the value is already a wire id).
     */
    private static final Map<String, String> REVIEWED = new LinkedHashMap<>();

    static {
        reviewed("habbohotel/catalog/CatalogItem.java :: this.itemId", "catalogue offer product list (base item ids)");
        reviewed("habbohotel/catalog/marketplace/MarketPlace.java :: itemId", "marketplace statistics by furni type");
        reviewed("habbohotel/games/snowwar/", "SnowWar game object ids, not furni");
        reviewed("messages/outgoing/snowwar/", "SnowWar game object ids, not furni");
        reviewed("messages/outgoing/catalog/catalogadmin/", "catalogue studio entity ids (pages, offers)");
        reviewed(
                "messages/outgoing/catalog/marketplace/MarketplaceOffersComposer.java :: offer.getItemId()",
                "marketplace offers: furni out of every room, never Builders Club");
        reviewed(
                "messages/outgoing/catalog/marketplace/MarketplaceOwnItemsComposer.java :: offer.getItemId()",
                "marketplace offers: furni out of every room, never Builders Club");
        reviewed(
                "messages/outgoing/events/mysticbox/MysticBoxPrizeComposer.java :: this.itemId",
                "the prize's furni class id");
        reviewed(
                "messages/outgoing/furnieditor/FurniEditorResultComposer.java :: this.id",
                "furni definition (base item) id");
        reviewed(
                "messages/outgoing/hotelview/HotelViewNextLTDAvailableComposer.java :: this.itemId",
                "catalogue item id");
        reviewed(
                "messages/outgoing/housekeeping/HousekeepingListComposer.java :: this.targetId",
                "housekeeping list target (user or room)");
        reviewed(
                "messages/outgoing/inventory/AddHabboItemComposer.java :: id",
                "unseen ids of any inventory category (furni, pets, bots, badges); inventory furni are never Builders Club");
        reviewed(
                "messages/outgoing/rooms/competition/IsUserPartOfCompetitionComposer.java :: this.targetId",
                "competition goal target");
        reviewed(
                "messages/outgoing/rooms/items/ChestFurniDeltaComposer.java :: id",
                "ids of furni stored inside a chest, not room objects");
        reviewed("messages/outgoing/rooms/items/jukebox/JukeBoxPlayListComposer.java :: soundTrack.getId()", "song id");
        reviewed("messages/outgoing/wired/WiredLabelsComposer.java :: label.getId()", "wired label id");
        reviewed("messages/outgoing/wired/WiredLabelsUpdateComposer.java :: label.getId()", "wired label id");
        reviewed(
                "messages/outgoing/wired/WiredUserVariablesDataComposer.java :: definition.getItemId()",
                "variable id: variables are keyed by their definition box's database id on both sides (stored in box string params, never a room object reference)");
        reviewed(
                "messages/outgoing/wired/WiredVariablesForObjectComposer.java :: this.inspection.getObjectId()",
                "the user branch writes a room index; the furni branch is translated");
        reviewed(
                "messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java :: itemId = this.packet.readInt()",
                "catalogue item id");
        reviewed(
                "messages/incoming/catalog/CatalogPurchaseCommandReader.java :: itemId = packet.readInt()",
                "catalogue item id");
        reviewed("messages/incoming/furnieditor/", "furni definition (base item) ids");
        reviewed("messages/incoming/housekeeping/", "housekeeping: base item and user/room ids");
        reviewed(
                "messages/incoming/inventory/UnseenResetItemsEvent.java :: itemIds.add(this.packet.readInt())",
                "unseen ids of any inventory category");
        reviewed(
                "messages/incoming/rooms/items/rentable/GetRentOrBuyoutOfferEvent.java :: furniTypeName = this.packet.readString()",
                "furni class name");
        reviewed(
                "messages/incoming/rooms/promotions/BuyRoomPromotionEvent.java :: itemId = this.packet.readInt()",
                "catalogue offer id");
        reviewed("messages/incoming/snowwar/", "SnowWar game object ids, not furni");
        reviewed(
                "messages/incoming/wired/WiredConditionSaveAdapter.java :: save",
                "room-less overload kept for plugin binaries; the save event passes settings it already translated");
    }

    private static void reviewed(String finding, String reason) {
        REVIEWED.put(BASE + finding, reason);
    }

    /** A finding is reviewed when it is listed, or lies under a listed folder (a key ending in '/'). */
    private static boolean isReviewed(String finding) {
        for (String key : REVIEWED.keySet()) {
            if (key.endsWith("/") ? finding.startsWith(key) : finding.equals(key)) return true;
        }
        return false;
    }

    @Test
    void everyItemIdWrittenToTheClientIsTranslated() throws IOException {
        Sources sources = Sources.load();
        Set<String> findings = new TreeSet<>();
        for (Source source : sources.list) {
            if (OUTGOING_SCOPE.stream().noneMatch(source.path::startsWith)) continue;
            for (MethodCallExpr call : source.unit.findAll(MethodCallExpr.class)) {
                if (!isPacketWrite(call)) continue;
                if (insideMethodNamedLike(call, "serialize") && sources.isWiredBox(source)) {
                    // Rewritten by WiredDefinitionWire.writeOfficial, which translates the box id and both picks.
                    continue;
                }
                for (Expression argument : call.getArguments()) {
                    for (Expression id : itemIds(argument, sources)) {
                        if (!translatedBy(id, argument, "toWire")) {
                            findings.add(source.path + " :: " + id);
                        }
                    }
                }
            }
        }
        findings.removeIf(BuildersClubItemIdCoverageTest::isReviewed);
        assertTrue(
                findings.isEmpty(),
                () -> "Item ids written without BuildersClubItemIds.toWire (" + findings.size() + "):\n"
                        + String.join("\n", findings));
    }

    @Test
    void everyItemIdReadFromTheClientIsTranslated() throws IOException {
        Sources sources = Sources.load();
        Set<String> findings = new TreeSet<>();
        for (Source source : sources.list) {
            if (INCOMING_SCOPE.stream().noneMatch(source.path::startsWith)) continue;
            for (CallableDeclaration<?> method : source.unit.findAll(CallableDeclaration.class)) {
                Map<String, Expression> packetLocals = new HashMap<>();
                for (VariableDeclarator variable : method.findAll(VariableDeclarator.class)) {
                    variable.getInitializer()
                            .filter(BuildersClubItemIdCoverageTest::readsPacket)
                            .ifPresent(init -> packetLocals.put(variable.getNameAsString(), init));
                }
                for (AssignExpr assign : method.findAll(AssignExpr.class)) {
                    if (readsPacket(assign.getValue()) && isItemIdTarget(assign.getTarget())) {
                        if (!translatedBy(packetRead(assign.getValue()), assign.getValue(), "fromWire")) {
                            findings.add(source.path + " :: " + assign);
                        }
                    }
                }
                for (Map.Entry<String, Expression> local : packetLocals.entrySet()) {
                    String name = local.getKey();
                    boolean itemId = isItemIdName(name) || usedAsLookup(method, name);
                    if (itemId && !translatedBy(packetRead(local.getValue()), local.getValue(), "fromWire")) {
                        findings.add(source.path + " :: " + name + " = " + local.getValue());
                    }
                }
                for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                    if (LOOKUPS.contains(call.getNameAsString())) {
                        for (Expression argument : call.getArguments()) {
                            if (readsPacket(argument) && !translatedBy(packetRead(argument), argument, "fromWire")) {
                                findings.add(source.path + " :: " + call);
                            }
                        }
                    }
                    if (call.getNameAsString().equals("add") && call.getScope().isPresent()) {
                        String target = call.getScope().get().toString();
                        for (Expression argument : call.getArguments()) {
                            if (readsPacket(argument)
                                    && isItemIdsName(lastSegment(target))
                                    && !translatedBy(packetRead(argument), argument, "fromWire")) {
                                findings.add(source.path + " :: " + call);
                            }
                        }
                    }
                }
            }
        }
        findings.removeIf(BuildersClubItemIdCoverageTest::isReviewed);
        assertTrue(
                findings.isEmpty(),
                () -> "Item ids read without BuildersClubItemIds.fromWire (" + findings.size() + "):\n"
                        + String.join("\n", findings));
    }

    @Test
    void everyWiredSaveTranslatesItsFurniPicks() throws IOException {
        Sources sources = Sources.load();
        Set<String> findings = new TreeSet<>();
        for (Source source : sources.list) {
            if (!source.path.startsWith(BASE + "messages/incoming/")) continue;
            for (CallableDeclaration<?> method : source.unit.findAll(CallableDeclaration.class)) {
                boolean reads = method.findAll(MethodCallExpr.class).stream()
                        .anyMatch(call -> call.getNameAsString().equals("readSettings"));
                boolean translates = method.findAll(MethodCallExpr.class).stream()
                        .anyMatch(call -> call.getNameAsString().equals("fromWire")
                                && call.getArguments().stream()
                                        .anyMatch(
                                                argument -> argument.toString().contains("readSettings")));
                if (reads && !translates) {
                    findings.add(source.path + " :: " + method.getNameAsString());
                }
            }
        }
        findings.removeIf(BuildersClubItemIdCoverageTest::isReviewed);
        assertTrue(
                findings.isEmpty(),
                () -> "Wired saves that keep the client's band ids:\n" + String.join("\n", findings));
    }

    // ==================== analysis ====================

    private static boolean isPacketWrite(MethodCallExpr call) {
        String name = call.getNameAsString();
        return name.equals("appendInt") || name.equals("appendString") || name.equals("appendLong");
    }

    private static boolean readsPacket(Expression expression) {
        return packetRead(expression) != null;
    }

    private static Expression packetRead(Expression expression) {
        for (MethodCallExpr call : expression.findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            if ((name.equals("readInt") || name.equals("readString"))
                    && call.getScope().map(s -> s.toString().endsWith("packet")).orElse(false)) {
                return call;
            }
        }
        if (expression instanceof MethodCallExpr call
                && (call.getNameAsString().equals("readInt")
                        || call.getNameAsString().equals("readString"))) {
            return call;
        }
        return null;
    }

    private static boolean translatedBy(Expression id, Expression root, String translator) {
        if (id == null) return true;
        Node node = id;
        while (node != null) {
            if (node instanceof MethodCallExpr call && call.getNameAsString().equals(translator)) {
                return true;
            }
            if (node == root) break;
            node = node.getParentNode().orElse(null);
        }
        return false;
    }

    private static boolean isItemIdTarget(Expression target) {
        String text = target.isArrayAccessExpr()
                ? target.asArrayAccessExpr().getName().toString()
                : target.toString();
        String name = lastSegment(text);
        return target.isArrayAccessExpr() ? isItemIdsName(name) : isItemIdName(name);
    }

    private static boolean usedAsLookup(CallableDeclaration<?> method, String name) {
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            if (!LOOKUPS.contains(call.getNameAsString())) continue;
            for (Expression argument : call.getArguments()) {
                if (argument.isNameExpr()
                        && argument.asNameExpr().getNameAsString().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String lastSegment(String text) {
        int dot = text.lastIndexOf('.');
        String name = dot >= 0 ? text.substring(dot + 1) : text;
        return name.endsWith("()") ? name.substring(0, name.length() - 2) : name;
    }

    private static boolean isItemIdName(String name) {
        return ITEM_ID_NAME.matcher(name).matches()
                && !NOT_A_FURNI_ID.matcher(name).matches();
    }

    private static boolean isItemIdsName(String name) {
        return ITEM_IDS_NAME.matcher(name).matches()
                && !NOT_A_FURNI_ID.matcher(name).matches();
    }

    /** The sub-expressions of {@code expression} that are furni ids. */
    private static List<Expression> itemIds(Expression expression, Sources sources) {
        List<Expression> ids = new ArrayList<>();
        Deque<Node> todo = new ArrayDeque<>();
        todo.add(expression);
        while (!todo.isEmpty()) {
            Node node = todo.poll();
            if (node instanceof LambdaExpr) continue;
            if (node instanceof MethodCallExpr call) {
                String name = call.getNameAsString();
                if (name.equals("toWire")) continue;
                if (name.equals("getId") && call.getArguments().isEmpty() && isItemScope(call, sources)) {
                    ids.add(call);
                    continue;
                }
                if (name.equals("getTeleportTargetId") || name.equals("getGiftAdjustedId")) {
                    ids.add(call);
                    continue;
                }
                if (call.getArguments().isEmpty() && isItemIdName(name)) {
                    ids.add(call);
                    continue;
                }
            }
            if (node instanceof NameExpr nameExpr) {
                String name = nameExpr.getNameAsString();
                if (isItemIdName(name) || isForEachOverItemIds(nameExpr) || isBareFurniId(name, nameExpr)) {
                    ids.add(nameExpr);
                    continue;
                }
            }
            if (node instanceof FieldAccessExpr field && field.getScope() instanceof ThisExpr) {
                if (isItemIdName(field.getNameAsString()) || isBareFurniId(field.getNameAsString(), field)) {
                    ids.add(field);
                    continue;
                }
            }
            todo.addAll(node.getChildNodes());
        }
        return ids;
    }

    private static boolean isForEachOverItemIds(NameExpr name) {
        Node node = name;
        while ((node = node.getParentNode().orElse(null)) != null) {
            if (node instanceof ForEachStmt loop
                    && loop.getVariable().getVariables().stream()
                            .anyMatch(v -> v.getNameAsString().equals(name.getNameAsString()))) {
                return isItemIdsName(lastSegment(loop.getIterable().toString()));
            }
        }
        return false;
    }

    private static boolean isItemScope(MethodCallExpr getId, Sources sources) {
        Optional<Expression> scope = getId.getScope();
        if (scope.isEmpty() || scope.get() instanceof ThisExpr) {
            return enclosingTypes(getId).stream().anyMatch(sources::isItemType);
        }
        Expression expression = scope.get();
        while (expression.isEnclosedExpr())
            expression = expression.asEnclosedExpr().getInner();
        if (expression.isCastExpr()) {
            return sources.isItemType(simpleName(expression.asCastExpr().getType()));
        }
        if (expression instanceof MethodCallExpr call) {
            String name = call.getNameAsString();
            if (sources.returnsItem(name)) return true;
            return name.equals("getHabboItem")
                    || name.equals("getItem")
                            && !call.getScope().map(Object::toString).orElse("").contains("Manager")
                    || name.equals("getTriggerItem")
                    || name.equals("triggerItem")
                    || name.equals("item")
                    || name.equals("furni");
        }
        String name = expression instanceof FieldAccessExpr field
                ? field.getNameAsString()
                : expression instanceof NameExpr nameExpr ? nameExpr.getNameAsString() : null;
        if (name == null) return false;
        String type = declaredType(getId, name, expression instanceof FieldAccessExpr);
        if (type == null && expression instanceof FieldAccessExpr field && !(field.getScope() instanceof ThisExpr)) {
            // Another object's field: any class declaring a field of that name with an item type.
            return sources.itemFieldNames.contains(name);
        }
        return type != null && sources.isItemType(type);
    }

    private static List<String> enclosingTypes(Node node) {
        List<String> types = new ArrayList<>();
        Node current = node;
        while ((current = current.getParentNode().orElse(null)) != null) {
            if (current instanceof TypeDeclaration<?> type) types.add(type.getNameAsString());
        }
        return types;
    }

    /** The simple type name {@code name} is declared with where {@code use} sees it, or null. */
    private static String declaredType(Node use, String name, boolean fieldOnly) {
        Node current = use;
        while ((current = current.getParentNode().orElse(null)) != null) {
            if (!fieldOnly) {
                if (current instanceof CallableDeclaration<?> callable) {
                    for (Parameter parameter : callable.getParameters()) {
                        if (parameter.getNameAsString().equals(name)) return simpleName(parameter.getType());
                    }
                    for (VariableDeclarator variable : callable.findAll(VariableDeclarator.class)) {
                        if (variable.getNameAsString().equals(name)) return simpleName(variable.getType());
                    }
                }
                if (current instanceof LambdaExpr lambda) {
                    for (Parameter parameter : lambda.getParameters()) {
                        if (parameter.getNameAsString().equals(name)) {
                            return parameter.getType().isUnknownType() ? null : simpleName(parameter.getType());
                        }
                    }
                }
            }
            if (current instanceof RecordDeclaration record) {
                for (Parameter parameter : record.getParameters()) {
                    if (parameter.getNameAsString().equals(name)) return simpleName(parameter.getType());
                }
            }
            if (current instanceof TypeDeclaration<?> type) {
                for (FieldDeclaration field : type.getFields()) {
                    for (VariableDeclarator variable : field.getVariables()) {
                        if (variable.getNameAsString().equals(name)) return simpleName(variable.getType());
                    }
                }
            }
        }
        return null;
    }

    private static String simpleName(Type type) {
        if (type.isArrayType()) return simpleName(type.asArrayType().getComponentType());
        if (type instanceof ClassOrInterfaceType classType) return classType.getNameAsString();
        return type.asString();
    }

    private static boolean insideMethodNamedLike(Node node, String prefix) {
        Node current = node;
        while ((current = current.getParentNode().orElse(null)) != null) {
            if (current instanceof CallableDeclaration<?> callable) {
                return callable.getNameAsString().startsWith(prefix);
            }
        }
        return false;
    }

    private static boolean isBareFurniId(String name, Node use) {
        if (!name.equals("id")) return false;
        List<String> types = enclosingTypes(use);
        return !types.isEmpty() && FURNI_CLASS.matcher(types.get(0)).matches();
    }

    private record Source(String path, CompilationUnit unit) {}

    private static final class Sources {
        private static Sources cached;
        final List<Source> list = new ArrayList<>();
        final Map<String, Set<String>> supertypes = new HashMap<>();
        final Map<String, Boolean> itemTypes = new HashMap<>();
        final Map<String, Boolean> wiredBoxes = new HashMap<>();
        final Set<String> itemFieldNames = new HashSet<>();
        final Set<String> itemMethodNames = new HashSet<>();
        private final Map<String, Set<String>> fieldTypes = new HashMap<>();
        private final Map<String, Set<String>> methodTypes = new HashMap<>();

        boolean returnsItem(String method) {
            return this.itemMethodNames.contains(method);
        }

        static synchronized Sources load() throws IOException {
            if (cached != null) return cached;
            StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
            Sources sources = new Sources();
            try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
                for (Path file : paths.filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .toList()) {
                    String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                    CompilationUnit unit = StaticJavaParser.parse(file);
                    sources.list.add(new Source(relative, unit));
                    for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
                        for (VariableDeclarator variable : field.getVariables()) {
                            sources.fieldTypes
                                    .computeIfAbsent(variable.getNameAsString(), ignored -> new HashSet<>())
                                    .add(simpleName(variable.getType()));
                        }
                    }
                    for (RecordDeclaration record : unit.findAll(RecordDeclaration.class)) {
                        for (Parameter parameter : record.getParameters()) {
                            sources.methodTypes
                                    .computeIfAbsent(parameter.getNameAsString(), ignored -> new HashSet<>())
                                    .add(simpleName(parameter.getType()));
                        }
                    }
                    for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
                        if (method.getParameters().isEmpty()) {
                            sources.methodTypes
                                    .computeIfAbsent(method.getNameAsString(), ignored -> new HashSet<>())
                                    .add(simpleName(method.getType()));
                        }
                    }
                    for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                        Set<String> supers =
                                sources.supertypes.computeIfAbsent(type.getNameAsString(), ignored -> new HashSet<>());
                        type.getExtendedTypes().forEach(t -> supers.add(t.getNameAsString()));
                    }
                }
            }
            sources.fieldTypes.forEach((name, types) -> {
                if (types.stream().anyMatch(sources::isItemType)) sources.itemFieldNames.add(name);
            });
            sources.methodTypes.forEach((name, types) -> {
                if (types.stream().anyMatch(sources::isItemType)) sources.itemMethodNames.add(name);
            });
            cached = sources;
            return sources;
        }

        boolean isItemType(String type) {
            return extendsType(type, "HabboItem", this.itemTypes, new HashSet<>());
        }

        boolean isWiredBox(Source source) {
            return source.unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .anyMatch(type ->
                            extendsType(type.getNameAsString(), "InteractionWired", this.wiredBoxes, new HashSet<>()));
        }

        private boolean extendsType(String type, String root, Map<String, Boolean> memo, Set<String> seen) {
            if (type == null) return false;
            if (type.equals(root)) return true;
            Boolean known = memo.get(type);
            if (known != null) return known;
            if (!seen.add(type)) return false;
            boolean result = false;
            for (String parent : this.supertypes.getOrDefault(type, Set.of())) {
                if (extendsType(parent, root, memo, seen)) {
                    result = true;
                    break;
                }
            }
            memo.put(type, result);
            return result;
        }
    }
}
