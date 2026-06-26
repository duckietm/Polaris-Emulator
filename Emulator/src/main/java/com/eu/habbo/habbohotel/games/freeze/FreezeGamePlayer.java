package com.eu.habbo.habbohotel.games.freeze;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.FreezeLivesComposer;

public class FreezeGamePlayer extends GamePlayer {
    public boolean nextDiagonal;
    public boolean nextHorizontal;
    public boolean tempMassiveExplosion;
    public boolean dead;
    private int lives;
    private int snowBalls;
    private int explosionBoost;
    private int protectionTime;
    private int frozenTime;

    public FreezeGamePlayer(Habbo habbo, GameTeamColors teamColor) {
        super(habbo, teamColor);
    }

    @Override
    public void reset() {
        this.lives = 3;
        this.snowBalls = 1;
        this.explosionBoost = 0;
        this.protectionTime = 0;
        this.frozenTime = 0;
        this.nextDiagonal = false;
        this.nextHorizontal = true;
        this.tempMassiveExplosion = false;
        this.dead = false;

        super.reset();
    }

    @Override
    public void addScore(int amount) {
        super.addScore(amount);

        if (amount > 0) {
            AchievementManager.progressAchievement(this.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("FreezePlayer"), amount);
        }
    }


    public void addLife() {
        if (this.lives < FreezeGame.MAX_LIVES) {
            this.lives++;
            super.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new FreezeLivesComposer(this).compose());
        }
    }

    public void takeLife() {
        this.lives--;
        if (this.lives == 0) {
            this.dead = true;

            var game = (FreezeGame) super.getHabbo().getHabboInfo().getCurrentRoom().getGame(FreezeGame.class);

            if (game != null) {
                game.playerDies(this);
            }
        } else {
            super.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new FreezeLivesComposer(this).compose());
        }
    }

    public int getLives() {
        return this.lives;
    }

    public boolean canPickupLife() {
        return this.lives < 3;
    }

    public void addSnowball() {
        if (this.snowBalls < FreezeGame.MAX_SNOWBALLS)
            this.snowBalls++;
    }

    public void addSnowball(int amount) {
        this.snowBalls += amount;

        if (this.snowBalls < 1)
            this.snowBalls = 1;
    }

    public void takeSnowball() {
        if (this.snowBalls > 0)
            this.snowBalls--;
    }

    public boolean canThrowSnowball() {
        return this.snowBalls > 0 && !this.isFrozen();
    }

    public void freeze() {
        if (this.protectionTime > 0 || this.frozenTime > 0)
            return;

        this.takeLife();

        this.frozenTime = FreezeGame.FREEZE_TIME;
        this.addSnowball(-FreezeGame.FREEZE_LOOSE_SNOWBALL);
        this.addExplosion(-FreezeGame.FREEZE_LOOSE_BOOST);
        super.getHabbo().getRoomUnit().setCanWalk(false);
        this.updateEffect();
    }

    public void unfreeze() {
        super.getHabbo().getRoomUnit().setCanWalk(true);
        this.frozenTime = 0;
        this.addProtection();
    }

    public boolean isFrozen() {
        return this.frozenTime > 0;
    }

    public boolean canGetFrozen() {
        return !this.isFrozen() && !this.isProtected();
    }

    public void addProtection() {
        this.updateEffect();

        if (this.isProtected() && !FreezeGame.POWERUP_STACK)
            return;

        this.protectionTime += FreezeGame.POWER_UP_PROTECT_TIME;
    }

    public boolean isProtected() {
        return this.protectionTime > 0;
    }

    public int getExplosionBoost() {
        if (this.tempMassiveExplosion) {
            this.tempMassiveExplosion = false;
            return 5;
        }

        return this.explosionBoost;
    }

    public void increaseExplosion() {
        if (this.explosionBoost < 5)
            this.explosionBoost++;
    }

    public void addExplosion(int radius) {
        this.explosionBoost += radius;

        if (this.explosionBoost < 0) {
            this.explosionBoost = 0;
        }

        if (this.explosionBoost > 5) {
            this.explosionBoost = 5;
        }
    }

    public void cycle() {
        boolean needsEffectUpdate = false;

        if (this.isProtected()) {
            this.protectionTime--;

            if (!this.isProtected())
                needsEffectUpdate = true;
        }

        if (this.frozenTime > 0) {
            this.frozenTime--;

            if (this.frozenTime <= 0) {
                super.getHabbo().getRoomUnit().setCanWalk(true);
                needsEffectUpdate = true;
            }
        }

        if (needsEffectUpdate)
            this.updateEffect();
    }

    public int correctEffectId() {
        if (this.dead)
            return 0;

        if (!this.isFrozen()) {
            int effectId = FreezeGame.effectId;

            effectId += super.getTeamColor().type;

            if (this.isProtected()) {
                effectId += 9;
            }

            return effectId;
        } else {
            return 12;
        }
    }

    public void updateEffect() {
        if (this.dead)
            return;

        super.getHabbo().getHabboInfo().getCurrentRoom().giveEffect(super.getHabbo(), this.correctEffectId(), -1);
    }
}
