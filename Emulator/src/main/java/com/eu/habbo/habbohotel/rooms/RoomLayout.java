package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.pathfinding.Pathfinder;
import com.eu.habbo.habbohotel.rooms.pathfinding.impl.PathfinderImpl;
import gnu.trove.set.hash.THashSet;
import java.awt.Rectangle;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomLayout {

  private static final Logger LOGGER = LoggerFactory.getLogger(RoomLayout.class);
  protected static final int BASICMOVEMENTCOST = 10;
  protected static final int DIAGONALMOVEMENTCOST = 14;
  public static double MAXIMUM_STEP_HEIGHT = 1.5;
  public static boolean ALLOW_FALLING = true;
  public static double UNDERPASS_HEIGHT = 1.5;
  public boolean CANMOVEDIAGONALY = true;
  private String name;
  private short doorX;
  private short doorY;
  private short doorZ;
  private int doorDirection;
  private String heightmap;
  private int mapSize;
  private int mapSizeX;
  private int mapSizeY;
  private RoomTile[][] roomTiles;
  private RoomTile doorTile;
  private Room room;
  private Pathfinder pathfinder;

  public RoomLayout(ResultSet set, Room room) throws SQLException {
    this.room = room;
    try {
      this.name = set.getString("name");
      this.doorX = set.getShort("door_x");
      this.doorY = set.getShort("door_y");

      this.doorDirection = set.getInt("door_dir");
      this.heightmap = set.getString("heightmap");

      this.parse();
      this.pathfinder = new PathfinderImpl(this.room, MAXIMUM_STEP_HEIGHT,
          Emulator.getConfig().getBoolean("pathfinder.step.allow.falling", true),
          Emulator.getConfig().getBoolean("pathfinder.retro-style.diagonals", false));
    } catch (Exception e) {
      LOGGER.error("Caught exception", e);
    }
  }

  public RoomLayout(RoomManager.RoomLayoutData data, Room room) {
    this.room = room;
    try {
      this.name = data.name;
      this.doorX = (short) data.doorX;
      this.doorY = (short) data.doorY;

      this.doorDirection = data.doorDir;
      this.heightmap = data.heightmap;

      this.parse();
      this.pathfinder = new PathfinderImpl(this.room, MAXIMUM_STEP_HEIGHT,
          Emulator.getConfig().getBoolean("pathfinder.step.allow.falling", true),
          Emulator.getConfig().getBoolean("pathfinder.retro-style.diagonals", false));
    } catch (Exception e) {
      LOGGER.error("Caught exception", e);
    }
  }

  public static boolean squareInSquare(Rectangle outerSquare, Rectangle innerSquare) {
    if (outerSquare.x > innerSquare.x) {
      return false;
    }

    if (outerSquare.y > innerSquare.y) {
      return false;
    }

    if (outerSquare.x + outerSquare.width < innerSquare.x + innerSquare.width) {
      return false;
    }

    return outerSquare.y + outerSquare.height >= innerSquare.y + innerSquare.height;
  }

  public static boolean tileInSquare(Rectangle square, RoomTile tile) {
    return (square.contains(tile.x, tile.y));
  }

  public static boolean pointInSquare(int x1, int y1, int x2, int y2, int pointX, int pointY) {
    return (pointX >= x1 && pointY >= y1) && (pointX <= x2 && pointY <= y2);
  }

  public static boolean tilesAdjecent(RoomTile one, RoomTile two) {
    return !(one == null || two == null) && !(Math.abs(one.x - two.x) > 1) && !(
        Math.abs(one.y - two.y) > 1);
  }

  public static Rectangle getRectangle(int x, int y, int width, int length, int rotation) {
    rotation = (rotation % 8);

    if (rotation == 2 || rotation == 6) {
      return new Rectangle(x, y, length, width);
    }

    return new Rectangle(x, y, width, length);
  }

  public static boolean tilesAdjecent(RoomTile tile, RoomTile comparator, int width, int length,
      int rotation) {
    Rectangle rectangle = getRectangle(comparator.x, comparator.y, width, length, rotation);
    rectangle = new Rectangle(rectangle.x - 1, rectangle.y - 1, rectangle.width + 2,
        rectangle.height + 2);

    return rectangle.contains(tile.x, tile.y);
  }

  public void parse() {
    String[] modelTemp = this.heightmap.replace("\n", "").split(Character.toString('\r'));

    this.mapSize = 0;
    this.mapSizeX = modelTemp[0].length();
    this.mapSizeY = modelTemp.length;
    this.roomTiles = new RoomTile[this.mapSizeX][this.mapSizeY];

    for (short y = 0; y < this.mapSizeY; y++) {
      // A row shorter/longer than the model width (or empty) cannot be parsed
      // per-square. Previously such tiles were left null while tileExists()
      // still reported them present, causing NPEs in the coordinate accessors.
      // Fill them with INVALID tiles so every in-bounds coordinate is non-null.
      boolean validRow = !modelTemp[y].isEmpty() && modelTemp[y].length() == this.mapSizeX;

      for (short x = 0; x < this.mapSizeX; x++) {
        if (!validRow) {
          this.roomTiles[x][y] = new RoomTile(x, y, (short) 0, RoomTileState.INVALID, true);
          continue;
        }

        String square = modelTemp[y].substring(x, x + 1).trim().toLowerCase();
        RoomTileState state = RoomTileState.OPEN;
        short height = 0;
        if (square.equalsIgnoreCase("x")) {
          state = RoomTileState.INVALID;
        } else {
          if (square.isEmpty()) {
            height = 0;
          } else if (Emulator.isNumeric(square)) {
            height = Short.parseShort(square);
          } else {
            height = (short) (10 + "ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(square.toUpperCase()));
          }
        }
        this.mapSize += 1;

        this.roomTiles[x][y] = new RoomTile(x, y, height, state, true);
      }
    }

    this.doorTile = (this.doorX >= 0 && this.doorX < this.mapSizeX && this.doorY >= 0 && this.doorY < this.mapSizeY)
        ? this.roomTiles[this.doorX][this.doorY]
        : null;

    if (this.doorTile != null) {
      this.doorTile.setAllowStack(false);
      RoomTile doorFrontTile = this.getTileInFront(this.doorTile, this.doorDirection);

      if (doorFrontTile != null && this.tileExists(doorFrontTile.x, doorFrontTile.y)) {
        if (this.roomTiles[doorFrontTile.x][doorFrontTile.y].state != RoomTileState.INVALID) {
          if (this.doorZ != this.roomTiles[doorFrontTile.x][doorFrontTile.y].z
              || this.roomTiles[this.doorX][this.doorY].state
              != this.roomTiles[doorFrontTile.x][doorFrontTile.y].state) {
            this.doorZ = this.roomTiles[doorFrontTile.x][doorFrontTile.y].z;
            this.roomTiles[this.doorX][this.doorY].state = RoomTileState.OPEN;
          }
        }
      }
    }
  }

  public String getName() {
    return this.name;
  }

  public short getDoorX() {
    return this.doorX;
  }

  public void setDoorX(short doorX) {
    this.doorX = doorX;
  }

  public short getDoorY() {
    return this.doorY;
  }

  public void setDoorY(short doorY) {
    this.doorY = doorY;
  }

  public int getDoorZ() {
    return this.doorZ;
  }

  public RoomTile getDoorTile() {
    return this.doorTile;
  }

  public int getDoorDirection() {
    return this.doorDirection;
  }

  public void setDoorDirection(int doorDirection) {
    this.doorDirection = doorDirection;
  }

  public String getHeightmap() {
    return this.heightmap;
  }

  public void setHeightmap(String heightMap) {
    this.heightmap = heightMap;
  }

  public int getMapSize() {
    return this.mapSize;
  }

  public int getMapSizeX() {
    return this.mapSizeX;
  }

  public int getMapSizeY() {
    return this.mapSizeY;
  }

  public short getHeightAtSquare(int x, int y) {
    if (x < 0 || y < 0 || x >= this.getMapSizeX() || y >= this.getMapSizeY()) {
      return 0;
    }

    return this.roomTiles[x][y].z;
  }

  public double getStackHeightAtSquare(int x, int y) {
    if (x < 0 || y < 0 || x >= this.getMapSizeX() || y >= this.getMapSizeY()) {
      return 0;
    }

    return this.roomTiles[x][y].getStackHeight();
  }

  public double getRelativeHeightAtSquare(int x, int y) {
    if (x < 0 || y < 0 || x >= this.getMapSizeX() || y >= this.getMapSizeY()) {
      return 0;
    }

    return this.roomTiles[x][y].relativeHeight();
  }

  public double getFloorAltitude(int x, int y) {
    short baseHeight = this.getHeightAtSquare(x, y);
    int target = baseHeight + 1;

    for (int dy = -1; dy <= 1; dy++) {
      for (int dx = -1; dx <= 1; dx++) {
        if (dx == 0 && dy == 0) continue;
        if (this.getHeightAtSquare(x + dx, y + dy) == target) {
          return baseHeight + 0.5;
        }
      }
    }

    return baseHeight;
  }

  public RoomTile getTile(short x, short y) {
    if (this.tileExists(x, y)) {
      return this.roomTiles[x][y];
    }

    return null;
  }

  public boolean tileExists(short x, short y) {
    return !(x < 0 || y < 0 || x >= this.getMapSizeX() || y >= this.getMapSizeY());
  }

  public boolean tileWalkable(short x, short y) {
    return this.tileExists(x, y) && this.roomTiles[x][y].state == RoomTileState.OPEN
        && this.roomTiles[x][y].isWalkable();
  }

  public boolean isVoidTile(short x, short y) {
    if (!this.tileExists(x, y)) {
      return true;
    }
    return this.roomTiles[x][y].state == RoomTileState.INVALID;
  }

  public String getRelativeMap() {
    return this.heightmap.replace("\r\n", "\r");
  }//re

  public void moveDiagonally(boolean value) {
    this.CANMOVEDIAGONALY = value;
  }

  public RoomTile getTileInFront(RoomTile tile, int rotation) {
    return this.getTileInFront(tile, rotation, 0);
  }

  public RoomTile getTileInFront(RoomTile tile, int rotation, int offset) {
    int offsetX = 0;
    int offsetY = 0;

    rotation = rotation % 8;
    switch (rotation) {
      case 0:
        offsetY--;
        break;
      case 1:
        offsetX++;
        offsetY--;
        break;
      case 2:
        offsetX++;
        break;
      case 3:
        offsetX++;
        offsetY++;
        break;
      case 4:
        offsetY++;
        break;
      case 5:
        offsetX--;
        offsetY++;
        break;
      case 6:
        offsetX--;
        break;
      case 7:
        offsetX--;
        offsetY--;
        break;
    }

    short x = tile.x;
    short y = tile.y;

    for (int i = 0; i <= offset; i++) {
      x += offsetX;
      y += offsetY;
    }

    return this.getTile(x, y);
  }

  public List<RoomTile> getTilesInFront(RoomTile tile, int rotation, int amount) {
    List<RoomTile> tiles = new ArrayList<>(amount);
    RoomTile previous = tile;
    for (int i = 0; i < amount; i++) {
      RoomTile t = this.getTileInFront(previous, rotation, i);

      if (t != null) {
        tiles.add(t);
      } else {
        break;
      }
    }

    return tiles;
  }

  public List<RoomTile> getTilesAround(RoomTile tile) {
    return getTilesAround(tile, 0);
  }

  public List<RoomTile> getTilesAround(RoomTile tile, int directionOffset) {
    return getTilesAround(tile, directionOffset, true);
  }

  public List<RoomTile> getTilesAround(RoomTile tile, int directionOffset, boolean diagonal) {
    List<RoomTile> tiles = new ArrayList<>(diagonal ? 8 : 4);

    if (tile != null) {
      for (int i = 0; i < 8; i += (diagonal ? 1 : 2)) {
        RoomTile t = this.getTileInFront(tile, (i + directionOffset) % 8);
        if (t != null) {
          tiles.add(t);
        }
      }
    }

    return tiles;
  }

  public List<RoomTile> getWalkableTilesAround(RoomTile tile) {
    return getWalkableTilesAround(tile, 0);
  }

  public List<RoomTile> getWalkableTilesAround(RoomTile tile, int directionOffset) {
    List<RoomTile> availableTiles = new ArrayList<>(this.getTilesAround(tile, directionOffset));

    List<RoomTile> toRemove = new ArrayList<>();

    for (RoomTile t : availableTiles) {
      if (t == null || t.state != RoomTileState.OPEN || !t.isWalkable()) {
        toRemove.add(t);
      }
    }

    for (RoomTile t : toRemove) {
      availableTiles.remove(t);
    }

    return availableTiles;
  }

  public boolean fitsOnMap(RoomTile tile, int width, int length, int rotation) {
    if (tile != null) {
      if (rotation == 0 || rotation == 4) {
        for (short i = tile.x; i <= (tile.x + (width - 1)); i++) {
          for (short j = tile.y; j <= (tile.y + (length - 1)); j++) {
            RoomTile t = this.getTile(i, j);

            if (t == null || t.getState() == RoomTileState.INVALID) {
              return false;
            }
          }
        }
      } else if (rotation == 2 || rotation == 6) {
        for (short i = tile.x; i <= (tile.x + (length - 1)); i++) {
          for (short j = tile.y; j <= (tile.y + (width - 1)); j++) {
            RoomTile t = this.getTile(i, j);

            if (t == null || t.getState() == RoomTileState.INVALID) {
              return false;
            }
          }
        }
      } else if (rotation == 1 || rotation == 3 || rotation == 5 || rotation == 7) {
        RoomTile t = this.getTile(tile.x, tile.y);
        if (t == null || t.getState() == RoomTileState.INVALID) {
          return false;
        }
      }
    }

    return true;
  }

  public THashSet<RoomTile> getTilesAt(RoomTile tile, int width, int length, int rotation) {
    THashSet<RoomTile> pointList = new THashSet<>(width * length, 0.1f);

    if (tile != null) {
      if (rotation == 0 || rotation == 4) {
        for (short i = tile.x; i <= (tile.x + (width - 1)); i++) {
          for (short j = tile.y; j <= (tile.y + (length - 1)); j++) {
            RoomTile t = this.getTile(i, j);

            if (t != null) {
              pointList.add(t);
            }
          }
        }
      } else if (rotation == 2 || rotation == 6) {
        for (short i = tile.x; i <= (tile.x + (length - 1)); i++) {
          for (short j = tile.y; j <= (tile.y + (width - 1)); j++) {
            RoomTile t = this.getTile(i, j);

            if (t != null) {
              pointList.add(t);
            }
          }
        }
      } else if (rotation == 1 || rotation == 3 || rotation == 5 || rotation == 7) {
        RoomTile t = this.getTile(tile.x, tile.y);
        if (t != null) {
          pointList.add(t);
        }
      }
    }
    return pointList;
  }

  public Pathfinder getPathfinder() {
    return pathfinder;
  }

  public void setPathfinder(Pathfinder pathfinder) {
    this.pathfinder = pathfinder;
  }
}
