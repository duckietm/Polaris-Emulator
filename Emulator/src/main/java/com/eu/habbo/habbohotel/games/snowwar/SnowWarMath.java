package com.eu.habbo.habbohotel.games.snowwar;

/**
 * Deterministic integer math used by the SnowWar simulation.
 *
 * All lookup tables and algorithms are copied EXACTLY from
 * Upgrade/README.md section 12. Everything here MUST stay 32-bit signed
 * integer arithmetic (Java int) so the checksums match the client, which
 * runs the identical simulation with JS "| 0" semantics.
 */
public final class SnowWarMath {

    /**
     * Fast square root lookup table. Index: scaled input value,
     * value: approximate sqrt * 16. (README 12.1)
     */
    private static final int[] TABLE = {
            0, 16, 22, 27, 32, 35, 39, 42, 45, 48, 50, 53, 55, 57, 59, 61, 64, 65, 67, 69, 71, 73, 75, 76, 78,
            80, 81, 83, 84, 86, 87, 89, 90, 91, 93, 94, 96, 97, 98, 99, 101, 102, 103, 104, 106, 107, 108, 109,
            110, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 128, 128, 129, 130, 131,
            132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 144, 145, 146, 147, 148, 149, 150, 150,
            151, 152, 153, 154, 155, 155, 156, 157, 158, 159, 160, 160, 161, 162, 163, 163, 164, 165, 166, 167, 167,
            168, 169, 170, 170, 171, 172, 173, 173, 174, 175, 176, 176, 177, 178, 178, 179, 180, 181, 181, 182, 183,
            183, 184, 185, 185, 186, 187, 187, 188, 189, 189, 190, 191, 192, 192, 193, 193, 194, 195, 195, 196, 197,
            197, 198, 199, 199, 200, 201, 201, 202, 203, 203, 204, 204, 205, 206, 206, 207, 208, 208, 209, 209, 210,
            211, 211, 212, 212, 213, 214, 214, 215, 215, 216, 217, 217, 218, 218, 219, 219, 220, 221, 221, 222, 222,
            223, 224, 224, 225, 225, 226, 226, 227, 227, 228, 229, 229, 230, 230, 231, 231, 232, 232, 233, 234, 234,
            235, 235, 236, 236, 237, 237, 238, 238, 239, 240, 240, 241, 241, 242, 242, 243, 243, 244, 244, 245, 245,
            246, 246, 247, 247, 248, 248, 249, 249, 250, 250, 251, 251, 252, 252, 253, 253, 254, 254, 255
    };

    /**
     * Angle component lookup table. Index: (component * 256 / other_component),
     * value: angle in degrees (0-45 range). (README 12.1)
     */
    private static final int[] COMPONENT = {
            0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 6, 6, 7,
            7, 7, 7, 8, 8, 8, 8, 8, 9, 9, 9, 9, 10, 10, 10, 10, 10, 11, 11, 11, 11, 12, 12, 12, 12, 12, 13, 13,
            13, 13, 13, 14, 14, 14, 14, 15, 15, 15, 15, 15, 16, 16, 16, 16, 16, 17, 17, 17, 17, 17, 18, 18, 18,
            18, 18, 19, 19, 19, 19, 19, 20, 20, 20, 20, 20, 21, 21, 21, 21, 21, 22, 22, 22, 22, 22, 23, 23, 23,
            23, 23, 24, 24, 24, 24, 24, 24, 25, 25, 25, 25, 25, 26, 26, 26, 26, 26, 26, 27, 27, 27, 27, 27, 28,
            28, 28, 28, 28, 28, 29, 29, 29, 29, 29, 29, 30, 30, 30, 30, 30, 30, 31, 31, 31, 31, 31, 31, 32, 32,
            32, 32, 32, 32, 33, 33, 33, 33, 33, 33, 34, 34, 34, 34, 34, 34, 34, 35, 35, 35, 35, 35, 35, 36, 36,
            36, 36, 36, 36, 36, 37, 37, 37, 37, 37, 37, 37, 38, 38, 38, 38, 38, 38, 38, 39, 39, 39, 39, 39, 39,
            39, 39, 40, 40, 40, 40, 40, 40, 40, 41, 41, 41, 41, 41, 41, 41, 41, 42, 42, 42, 42, 42, 42, 42, 42,
            43, 43, 43, 43, 43, 43, 43, 43, 44, 44, 44, 44, 44, 44, 44, 44, 44, 45, 45, 45, 45, 45
    };

    /**
     * Base velocity X (horizontal) per direction in degrees (0-359).
     * 0 = East, 90 = South, 180 = West, 270 = North. (README 12.1)
     */
    private static final int[] BASE_VEL_X = {
            0, 4, 8, 13, 17, 22, 26, 31, 35, 40, 44, 48, 53, 57, 61, 66, 70, 74, 79, 83, 87, 91, 95, 100, 104,
            108, 112, 116, 120, 124, 127, 131, 135, 139, 143, 146, 150, 154, 157, 161, 164, 167, 171, 174, 177, 181,
            184, 187, 190, 193, 196, 198, 201, 204, 207, 209, 212, 214, 217, 219, 221, 223, 226, 228, 230, 232, 233,
            235, 237, 238, 240, 242, 243, 244, 246, 247, 248, 249, 250, 251, 252, 252, 253, 254, 254, 255, 255, 255,
            255, 255, 256, 255, 255, 255, 255, 255, 254, 254, 253, 252, 252, 251, 250, 249, 248, 247, 246, 244, 243,
            242, 240, 238, 237, 235, 233, 232, 230, 228, 226, 223, 221, 219, 217, 214, 212, 209, 207, 204, 201, 198,
            196, 193, 190, 187, 184, 181, 177, 174, 171, 167, 164, 161, 157, 154, 150, 146, 143, 139, 135, 131, 127,
            124, 120, 116, 112, 108, 104, 100, 95, 91, 87, 83, 79, 74, 70, 66, 61, 57, 53, 48, 44, 40, 35, 31, 26,
            22, 17, 13, 8, 4, 0, -4, -8, -13, -17, -22, -26, -31, -35, -40, -44, -48, -53, -57, -61, -66, -70, -74,
            -79, -83, -87, -91, -95, -100, -104, -108, -112, -116, -120, -124, -128, -131, -135, -139, -143, -146,
            -150, -154, -157, -161, -164, -167, -171, -174, -177, -181, -184, -187, -190, -193, -196, -198, -201, -204,
            -207, -209, -212, -214, -217, -219, -221, -223, -226, -228, -230, -232, -233, -235, -237, -238, -240, -242,
            -243, -244, -246, -247, -248, -249, -250, -251, -252, -252, -253, -254, -254, -255, -255, -255, -255, -255,
            -256, -255, -255, -255, -255, -255, -254, -254, -253, -252, -252, -251, -250, -249, -248, -247, -246, -244,
            -243, -242, -240, -238, -237, -235, -233, -232, -230, -228, -226, -223, -221, -219, -217, -214, -212, -209,
            -207, -204, -201, -198, -196, -193, -190, -187, -184, -181, -177, -174, -171, -167, -164, -161, -157, -154,
            -150, -146, -143, -139, -135, -131, -128, -124, -120, -116, -112, -108, -104, -100, -95, -91, -87, -83,
            -79, -74, -70, -66, -61, -57, -53, -48, -44, -40, -35, -31, -26, -22, -17, -13, -8, -4
    };

    /**
     * Base velocity Y (vertical) per direction in degrees (0-359).
     * Y is 90 degrees offset from X (cos/sin relationship). (README 12.1)
     */
    private static final int[] BASE_VEL_Y = {
            -256, -255, -255, -255, -255, -255, -254, -254, -253, -252, -252, -251, -250, -249, -248, -247, -246, -244,
            -243, -242, -240, -238, -237, -235, -233, -232, -230, -228, -226, -223, -221, -219, -217, -214, -212, -209,
            -207, -204, -201, -198, -196, -193, -190, -187, -184, -181, -177, -174, -171, -167, -164, -161, -157, -154,
            -150, -146, -143, -139, -135, -131, -128, -124, -120, -116, -112, -108, -104, -100, -95, -91, -87, -83, -79,
            -74, -70, -66, -61, -57, -53, -48, -44, -40, -35, -31, -26, -22, -17, -13, -8, -4, 0, 4, 8, 13, 17, 22,
            26, 31, 35, 40, 44, 48, 53, 57, 61, 66, 70, 74, 79, 83, 87, 91, 95, 100, 104, 108, 112, 116, 120, 124,
            127, 131, 135, 139, 143, 146, 150, 154, 157, 161, 164, 167, 171, 174, 177, 181, 184, 187, 190, 193, 196,
            198, 201, 204, 207, 209, 212, 214, 217, 219, 221, 223, 226, 228, 230, 232, 233, 235, 237, 238, 240, 242,
            243, 244, 246, 247, 248, 249, 250, 251, 252, 252, 253, 254, 254, 255, 255, 255, 255, 255, 256, 255, 255,
            255, 255, 255, 254, 254, 253, 252, 252, 251, 250, 249, 248, 247, 246, 244, 243, 242, 240, 238, 237, 235,
            233, 232, 230, 228, 226, 223, 221, 219, 217, 214, 212, 209, 207, 204, 201, 198, 196, 193, 190, 187, 184,
            181, 177, 174, 171, 167, 164, 161, 157, 154, 150, 146, 143, 139, 135, 131, 128, 124, 120, 116, 112, 108,
            104, 100, 95, 91, 87, 83, 79, 74, 70, 66, 61, 57, 53, 48, 44, 40, 35, 31, 26, 22, 17, 13, 8, 4, 0, -4,
            -8, -13, -17, -22, -26, -31, -35, -40, -44, -48, -53, -57, -61, -66, -70, -74, -79, -83, -87, -91, -95,
            -100, -104, -108, -112, -116, -120, -124, -128, -131, -135, -139, -143, -146, -150, -154, -157, -161, -164,
            -167, -171, -174, -177, -181, -184, -187, -190, -193, -196, -198, -201, -204, -207, -209, -212, -214, -217,
            -219, -221, -223, -226, -228, -230, -232, -233, -235, -237, -238, -240, -242, -243, -244, -246, -247, -248,
            -249, -250, -251, -252, -252, -253, -254, -254, -255, -255, -255, -255, -255
    };

    private SnowWarMath() {
    }

    // ========================================================================
    // Coordinate conversion (README 11.2)
    // ========================================================================

    public static int tileToWorld(int num) {
        return num * SnowWarConstants.TILE_SIZE;
    }

    public static int worldToTile(int num) {
        return (num + 1600) / SnowWarConstants.TILE_SIZE;
    }

    public static int convertToWorldCoordinate(int num) {
        return num * SnowWarConstants.TILE_SIZE;
    }

    public static int convertToGameCoordinate(int num) {
        return num / SnowWarConstants.TILE_SIZE;
    }

    // ========================================================================
    // Fast square root (README 12.2)
    // ========================================================================

    public static int fastSqrt(int x) {
        if (x >= 65536) {
            if (x >= 16777216) {
                if (x >= 268435456) {
                    if (x >= 1073741824) {
                        return TABLE[x / 16777216] * 256;
                    } else {
                        return TABLE[x / 4194304] * 128;
                    }
                } else {
                    if (x >= 67108864) {
                        return TABLE[x / 1048576] * 64;
                    } else {
                        return TABLE[x / 262144] * 32;
                    }
                }
            } else {
                if (x >= 1048576) {
                    if (x >= 4194304) {
                        return TABLE[x / 65536] * 16;
                    } else {
                        return TABLE[x / 16384] * 8;
                    }
                } else {
                    if (x >= 262144) {
                        return TABLE[x / 4096] * 4;
                    } else {
                        return TABLE[x / 1024] * 2;
                    }
                }
            }
        } else {
            if (x >= 256) {
                if (x >= 4096) {
                    if (x >= 16384) {
                        return TABLE[x / 256];
                    } else {
                        return TABLE[x / 64] / 2;
                    }
                } else {
                    if (x >= 1024) {
                        return TABLE[x / 16] / 4;
                    } else {
                        return TABLE[x / 4] / 8;
                    }
                }
            } else {
                if (x >= 0) {
                    return TABLE[x] / 16;
                }
            }
        }

        return -1;
    }

    // ========================================================================
    // Direction helpers (README 11.4)
    // ========================================================================

    public static int validateDirection360(int value) {
        if (value > 359) {
            return value % 360;
        }
        if (value < 0) {
            return 360 + (value % 360);
        }
        return value;
    }

    public static int validateDirection8(int value) {
        if (value > 7) {
            return value % 8;
        }
        if (value < 0) {
            return (8 + (value % 8)) % 8;
        }
        return value;
    }

    public static int direction360To8(int angle360) {
        int validated = validateDirection360(angle360 - 22);
        return validateDirection8((validated / 45) + 1);
    }

    public static int rotateDirection45DegreesCw(int value) {
        return validateDirection360(value + 45);
    }

    public static int rotateDirection45DegreesCcw(int value) {
        return validateDirection360(value - 45);
    }

    // ========================================================================
    // Angle from components (README 12.3)
    // ========================================================================

    public static int getAngleFromComponents(int x, int y) {
        return validateDirection360(getAngleFromComponentsMaths(x, y));
    }

    private static int getAngleFromComponentsMaths(int x, int y) {
        if (Math.abs(x) <= Math.abs(y)) {
            // Y component is dominant
            if (y == 0) {
                y = 1;
            }

            x = x * 256;
            int temp = x / y;

            if (temp < 0) {
                temp = -temp;
            }
            if (temp > 255) {
                temp = 255;
            }

            if (y < 0) {
                if (x > 0) {
                    return COMPONENT[temp];
                } else {
                    return 360 - COMPONENT[temp];
                }
            } else {
                if (x > 0) {
                    return 180 - COMPONENT[temp];
                } else {
                    return 180 + COMPONENT[temp];
                }
            }
        } else {
            // X component is dominant
            if (x == 0) {
                x = 1;
            }

            y = y * 256;
            int temp = y / x;

            if (temp < 0) {
                temp = -temp;
            }
            if (temp > 255) {
                temp = 255;
            }

            if (y < 0) {
                if (x > 0) {
                    return 90 - COMPONENT[temp];
                } else {
                    return 270 + COMPONENT[temp];
                }
            } else {
                if (x > 0) {
                    return 90 + COMPONENT[temp];
                } else {
                    return 270 - COMPONENT[temp];
                }
            }
        }
    }

    // ========================================================================
    // Velocity lookup (README 12.4)
    // ========================================================================

    public static int getBaseVelX(int direction) {
        return BASE_VEL_X[validateDirection360(direction)];
    }

    public static int getBaseVelY(int direction) {
        return BASE_VEL_Y[validateDirection360(direction)];
    }

    // ========================================================================
    // Checksum seed iterator, XOR-shift PRNG (README 12.5)
    // Java's << and >> match the spec's bitLeft/bitRight (arithmetic shift).
    // ========================================================================

    public static int iterateSeed(int seed) {
        if (seed == 0) {
            seed = -1;
        }

        int seed2 = seed << 13;
        seed = seed ^ seed2;

        seed2 = seed >> 17;
        seed = seed ^ seed2;

        seed2 = seed << 5;
        seed = seed ^ seed2;

        return seed;
    }

    // ========================================================================
    // Snowball flight path (README 12.6)
    // Input coordinates are TILE coordinates.
    // Returns [direction, timeToLive, parabolaOffset].
    // ========================================================================

    public static int[] calculateFlightPath(int userX, int userY, int targetX, int targetY, int trajectory) {
        int[] output = new int[3];

        int startWorldX = tileToWorld(userX);
        int startWorldY = tileToWorld(userY);
        int targetWorldX = tileToWorld(targetX);
        int targetWorldY = tileToWorld(targetY);

        int deltaX = (targetWorldX - startWorldX) / 200;
        int deltaY = (targetWorldY - startWorldY) / 200;

        output[0] = getAngleFromComponents(deltaX, deltaY);

        if (trajectory == 1) {
            output[1] = 13;
        } else {
            int distanceSquared = (deltaX * deltaX) + (deltaY * deltaY);
            int distanceToTarget = fastSqrt(distanceSquared) * 200;

            output[1] = distanceToTarget / 2000;
        }

        output[2] = output[1] / 2;

        return output;
    }
}
