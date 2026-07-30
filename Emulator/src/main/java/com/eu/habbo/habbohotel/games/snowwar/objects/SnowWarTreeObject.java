package com.eu.habbo.habbohotel.games.snowwar.objects;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarConstants;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarMath;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarItem;
import com.eu.habbo.messages.ServerMessage;

/** AIR TreeGameObject: three damage states and a strict circular hitbox. */
public class SnowWarTreeObject extends SnowWarGameObject {

    private final SnowWarItem item;
    private int hits;

    public SnowWarTreeObject(int objectId, SnowWarItem item) {
        super(objectId);
        this.item = item;
        this.hits = Math.min(SnowWarConstants.TREE_MAX_HITS, item.getState());
    }

    public int getX() {
        return this.item.getX();
    }

    public int getY() {
        return this.item.getY();
    }

    public int getHits() {
        return this.hits;
    }

    public int hit() {
        if (this.hits < SnowWarConstants.TREE_MAX_HITS) {
            this.hits++;
        }
        return this.hits;
    }

    public boolean testCollision(SnowWarSnowballObject ball) {
        return this.isAlive()
                && ball.getHeight() < SnowWarConstants.TREE_COLLISION_HEIGHT
                && SnowWarMath.circlesOverlap(
                        ball.getLocH(),
                        ball.getLocV(),
                        SnowWarConstants.SNOWBALL_RADIUS,
                        SnowWarMath.tileToWorld(this.getX()),
                        SnowWarMath.tileToWorld(this.getY()),
                        SnowWarConstants.TREE_RADIUS);
    }

    @Override
    public int[] getChecksumValues() {
        return new int[] {
            SnowWarConstants.OBJECT_TYPE_TREE,
            this.objectId,
            SnowWarMath.tileToWorld(this.getX()),
            SnowWarMath.tileToWorld(this.getY()),
            this.item.getRotation(),
            SnowWarConstants.TREE_COLLISION_HEIGHT,
            0,
            SnowWarConstants.TREE_MAX_HITS,
            this.hits
        };
    }

    @Override
    public void serialize(ServerMessage response) {
        for (int value : this.getChecksumValues()) {
            response.appendInt(value);
        }
    }

    @Override
    public boolean isAlive() {
        return this.hits < SnowWarConstants.TREE_MAX_HITS;
    }
}
