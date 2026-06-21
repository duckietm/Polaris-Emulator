package com.eu.habbo.habbohotel.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionNest;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetDrink;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetFood;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetToy;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PetData implements Comparable<PetData> {

    public static final String BLINK = "eyb";
    public static final String SPEAK = "spk";
    public static final String EAT = "eat";
    public static final String PLAYFUL = "pla";
    public static final List<Item> generalDrinkItems = new ArrayList<>();
    public static final List<Item> generalFoodItems = new ArrayList<>();
    public static final List<Item> generalNestItems = new ArrayList<>();
    public static final List<Item> generalToyItems = new ArrayList<>();
    public static final Map<PetVocalsType, Set<PetVocal>> generalPetVocals = new HashMap<>();
    public String[] actionsHappy;
    public String[] actionsTired;
    public String[] actionsRandom;
    public Map<PetVocalsType, Set<PetVocal>> petVocals;
    public boolean canSwim;
    private int type;
    private String name;
    private List<PetCommand> petCommands;
    private List<Item> nestItems;
    private List<Item> foodItems;
    private List<Item> drinkItems;
    private List<Item> toyItems;
    private int offspringType;

    public PetData(ResultSet set) throws SQLException {
        this.load(set);
    }

    public void load(ResultSet set) throws SQLException {
        this.type = set.getInt("pet_type");
        this.name = set.getString("pet_name");
        this.offspringType = set.getInt("offspring_type");
        this.actionsHappy = set.getString("happy_actions").split(";");
        this.actionsTired = set.getString("tired_actions").split(";");
        this.actionsRandom = set.getString("random_actions").split(";");
        this.canSwim = set.getString("can_swim").equalsIgnoreCase("1");

        this.reset();
    }

    public List<PetCommand> getPetCommands() {
        return this.petCommands;
    }

    public void setPetCommands(List<PetCommand> petCommands) {
        this.petCommands = petCommands;
    }

    public int getType() {
        return this.type;
    }


    public String getName() {
        return this.name;
    }


    public int getOffspringType() {
        return this.offspringType;
    }


    public void addNest(Item nest) {
        if (nest != null)
            this.nestItems.add(nest);
    }


    public List<Item> getNests() {
        return this.nestItems;
    }


    public boolean haveNest(HabboItem nest) {
        return this.haveNest(nest.getBaseItem());
    }


    boolean haveNest(Item nest) {
        // If no nest items are registered, allow all nest items
        if (this.nestItems.isEmpty() && PetData.generalNestItems.isEmpty()) {
            return true;
        }
        return PetData.generalNestItems.contains(nest) || this.nestItems.contains(nest);
    }


    public HabboItem randomNest(Set<InteractionNest> items) {
        List<HabboItem> nestList = new ArrayList<>();
        
        // If no nest items are registered, allow all nests in the room
        boolean allowAll = this.nestItems.isEmpty() && PetData.generalNestItems.isEmpty();

        for (InteractionNest nest : items) {
            if (allowAll || this.haveNest(nest)) {
                nestList.add(nest);
            }
        }

        if (!nestList.isEmpty()) {
            Collections.shuffle(nestList);

            return nestList.get(0);
        }

        return null;
    }


    public void addFoodItem(Item food) {
        this.foodItems.add(food);
    }


    public List<Item> getFoodItems() {
        return this.foodItems;
    }


    public boolean haveFoodItem(HabboItem food) {
        return this.haveFoodItem(food.getBaseItem());
    }


    boolean haveFoodItem(Item food) {
        // If no food items are registered, allow all food items
        if (this.foodItems.isEmpty() && PetData.generalFoodItems.isEmpty()) {
            return true;
        }
        return this.foodItems.contains(food) || PetData.generalFoodItems.contains(food);
    }


    public HabboItem randomFoodItem(Set<InteractionPetFood> items) {
        List<HabboItem> foodList = new ArrayList<>();
        
        // If no food items are registered, allow all food in the room
        boolean allowAll = this.foodItems.isEmpty() && PetData.generalFoodItems.isEmpty();

        for (InteractionPetFood food : items) {
            if (allowAll || this.haveFoodItem(food)) {
                foodList.add(food);
            }
        }

        if (!foodList.isEmpty()) {
            Collections.shuffle(foodList);
            return foodList.get(0);
        }

        return null;
    }


    public void addDrinkItem(Item item) {
        this.drinkItems.add(item);
    }


    public List<Item> getDrinkItems() {
        return this.drinkItems;
    }


    public boolean haveDrinkItem(HabboItem item) {
        return this.haveDrinkItem(item.getBaseItem());
    }


    boolean haveDrinkItem(Item item) {
        // If no drink items are registered, allow all drink items
        if (this.drinkItems.isEmpty() && PetData.generalDrinkItems.isEmpty()) {
            return true;
        }
        return this.drinkItems.contains(item) || PetData.generalDrinkItems.contains(item);
    }


    public HabboItem randomDrinkItem(Set<InteractionPetDrink> items) {
        List<HabboItem> drinkList = new ArrayList<>();
        
        // If no drink items are registered, allow all drinks in the room
        boolean allowAll = this.drinkItems.isEmpty() && PetData.generalDrinkItems.isEmpty();

        for (InteractionPetDrink drink : items) {
            if (allowAll || this.haveDrinkItem(drink)) {
                drinkList.add(drink);
            }
        }

        if (!drinkList.isEmpty()) {
            Collections.shuffle(drinkList);
            return drinkList.get(0);
        }

        return null;
    }


    public void addToyItem(Item toy) {
        this.toyItems.add(toy);
    }


    public List<Item> getToyItems() {
        return this.toyItems;
    }


    public boolean haveToyItem(HabboItem toy) {
        return this.haveToyItem(toy.getBaseItem());
    }


    public boolean haveToyItem(Item toy) {
        // If no toy items are registered, allow all toy items
        if (this.toyItems.isEmpty() && PetData.generalToyItems.isEmpty()) {
            return true;
        }
        return this.toyItems.contains(toy) || PetData.generalToyItems.contains(toy);
    }


    public HabboItem randomToyItem(Set<InteractionPetToy> toys) {
        List<HabboItem> toyList = new ArrayList<>();
        
        // If no toy items are registered, allow all toys in the room
        boolean allowAll = this.toyItems.isEmpty() && PetData.generalToyItems.isEmpty();

        for (InteractionPetToy toy : toys) {
            if (allowAll || this.haveToyItem(toy)) {
                toyList.add(toy);
            }
        }

        if (!toyList.isEmpty()) {
            Collections.shuffle(toyList);
            return toyList.get(0);
        }

        return null;
    }

    /**
     * Finds a random toy item from a generic set of HabboItems.
     * Used for finding pet items like trampolines, trees, etc.
     */
    public HabboItem randomToyHabboItem(Set<HabboItem> items) {
        List<HabboItem> itemList = new ArrayList<>();
        
        // If no toy items are registered, allow all toys in the room
        boolean allowAll = this.toyItems.isEmpty() && PetData.generalToyItems.isEmpty();

        for (HabboItem item : items) {
            if (allowAll || this.haveToyItem(item)) {
                itemList.add(item);
            }
        }

        if (!itemList.isEmpty()) {
            Collections.shuffle(itemList);
            return itemList.get(0);
        }

        return null;
    }


    public PetVocal randomVocal(PetVocalsType type) {
        Set<PetVocal> petTypeVocals = this.petVocals.get(type);
        Set<PetVocal> generalVocals = PetData.generalPetVocals.get(type);

        int petTypeSize = petTypeVocals != null ? petTypeVocals.size() : 0;
        int generalSize = generalVocals != null ? generalVocals.size() : 0;
        int totalSize = petTypeSize + generalSize;

        if (totalSize == 0) {
            // Return a default vocal instead of null
            return getDefaultVocal(type);
        }

        int randomIndex = Emulator.getRandom().nextInt(totalSize);

        if (randomIndex < petTypeSize) {
            int i = 0;
            for (PetVocal vocal : petTypeVocals) {
                if (i == randomIndex) return vocal;
                i++;
            }
        } else {
            int i = 0;
            int targetIndex = randomIndex - petTypeSize;
            for (PetVocal vocal : generalVocals) {
                if (i == targetIndex) return vocal;
                i++;
            }
        }

        return getDefaultVocal(type);
    }

    /**
     * Returns a default vocal message when no configured vocals exist for the type.
     * This prevents null pointer exceptions and silent pets.
     */
    private static PetVocal getDefaultVocal(PetVocalsType type) {
        return switch (type) {
            case GENERIC_HAPPY -> new PetVocal("*wags tail happily*");
            case GENERIC_SAD -> new PetVocal("*whimpers*");
            case GENERIC_NEUTRAL -> new PetVocal("*looks around*");
            case HUNGRY -> new PetVocal("*stomach growls*");
            case THIRSTY -> new PetVocal("*pants*");
            case TIRED -> new PetVocal("*yawns*");
            case SLEEPING -> new PetVocal("*snores softly*");
            case PLAYFUL -> new PetVocal("*bounces excitedly*");
            case DISOBEY -> new PetVocal("*ignores command*");
            case EATING -> new PetVocal("*munches happily*");
            case DRINKING -> new PetVocal("*laps up water*");
            case LEVEL_UP -> new PetVocal("*jumps with joy*");
            case GREET_OWNER -> new PetVocal("*perks up excitedly*");
            case MUTED -> new PetVocal("*stays quiet*");
            case UNKNOWN_COMMAND -> new PetVocal("*tilts head confused*");
        };
    }

    @Override
    public int compareTo(PetData o) {
        return this.getType() - o.getType();
    }

    public void reset() {
        this.petCommands = new ArrayList<>();
        this.nestItems = new ArrayList<>();
        this.foodItems = new ArrayList<>();
        this.drinkItems = new ArrayList<>();
        this.toyItems = new ArrayList<>();

        this.petVocals = new HashMap<>();

        for (PetVocalsType type : PetVocalsType.values()) {
            this.petVocals.put(type, new HashSet<>());
        }

        if (PetData.generalPetVocals.isEmpty()) {
            for (PetVocalsType type : PetVocalsType.values()) {
                PetData.generalPetVocals.put(type, new HashSet<>());
            }
        }
    }
}
