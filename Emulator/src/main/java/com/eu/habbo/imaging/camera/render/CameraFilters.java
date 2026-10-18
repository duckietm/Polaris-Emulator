package com.eu.habbo.imaging.camera.render;

public enum CameraFilters {
    DARK_SEPIA(
            "dark_sepia",
            FilterType.COLORMATRIX,
            new float[] {0.4f, 0.4f, 0.1f, 0, 110, 0.3f, 0.4f, 0.1f, 0, 30, 0.3f, 0.2f, 0.1f, 0, 0, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    DECREASE_SATURATION(
            "decrease_saturation",
            FilterType.COLORMATRIX,
            new float[] {0.7f, 0.2f, 0.2f, 0, 0, 0.2f, 0.7f, 0.2f, 0, 0, 0.2f, 0.2f, 0.7f, 0, 0, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    INCREASE_SATURATION(
            "increase_saturation",
            FilterType.COLORMATRIX,
            new float[] {2, -0.5f, -0.5f, 0, 0, -0.5f, 2, -0.5f, 0, 0, -0.5f, -0.5f, 2, 0, 0, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    DECR_CONRAST(
            "decr_conrast",
            FilterType.COLORMATRIX,
            new float[] {0.5f, 0, 0, 0, 50, 0, 0.5f, 0, 0, 50, 0, 0, 0.5f, 0, 50, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    INCREASE_CONTRAST(
            "increase_contrast",
            FilterType.COLORMATRIX,
            new float[] {1.5f, 0, 0, 0, -50, 0, 1.5f, 0, 0, -50, 0, 0, 1.5f, 0, -50, 0, 0, 0, 1.5f, 0},
            BlendMode.NULL),
    COLOR_1(
            "color_1",
            FilterType.COLORMATRIX,
            new float[] {
                0.393f, 0.769f, 0.189f, 0, 0, 0.349f, 0.686f, 0.168f, 0, 0, 0.272f, 0.534f, 0.131f, 0, 0, 0, 0, 0, 1, 0
            },
            BlendMode.NULL),
    COLOR_2(
            "color_2",
            FilterType.COLORMATRIX,
            new float[] {
                0.333f, 0.333f, 0.333f, 0, 0, 0.333f, 0.333f, 0.333f, 0, 0, 0.333f, 0.333f, 0.333f, 0, 0, 0, 0, 0, 1, 0
            },
            BlendMode.NULL),
    COLOR_3(
            "color_3",
            FilterType.COLORMATRIX,
            new float[] {
                0.609f, 0.609f, 0.082f, 0, 0, 0.309f, 0.609f, 0.082f, 0, 0, 0.309f, 0.609f, 0.082f, 0, 0, 0, 0, 0, 1, 0
            },
            BlendMode.NULL),
    COLOR_4(
            "color_4",
            FilterType.COLORMATRIX,
            new float[] {0.8f, -0.8f, 1, 0, 70, 0.8f, -0.8f, 1, 0, 70, 0.8f, -0.8f, 1, 0, 70, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    HUE_BRIGHT_SAT(
            "hue_bright_sat",
            FilterType.COLORMATRIX,
            new float[] {1, 0.6f, 0.2f, 0, -50, 0.2f, 1, 0.6f, 0, -50, 0.6f, 0.2f, 1, 0, -50, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    NIGHT_VISION(
            "night_vision",
            FilterType.COLORMATRIX,
            new float[] {0, 0, 0, 0, 0, 0, 1.1f, 0, 0, -50, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    HYPERSATURATED(
            "hypersaturated",
            FilterType.COLORMATRIX,
            new float[] {2, -1, 0, 0, 0, -1, 2, 0, 0, 0, 0, -1, 2, 0, 0, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    X_RAY(
            "x_ray",
            FilterType.COLORMATRIX,
            new float[] {0, 1.2f, 0, 0, -100, 0, 2, 0, 0, -120, 0, 2, 0, 0, -120, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    COLOR_5(
            "color_5",
            FilterType.COLORMATRIX,
            new float[] {
                3.309f, 0.609f, 1.082f, 0.2f, 0, 0.309f, 0.609f, 0.082f, 0, 0, 1.309f, 0.609f, 0.082f, 0, 0, 0, 0, 0, 1,
                0
            },
            BlendMode.NULL),
    BLACK_WHITE_NEGATIVE(
            "black_white_negative",
            FilterType.COLORMATRIX,
            new float[] {
                -0.5f, -0.5f, -0.5f, 0, 0xFF, -0.5f, -0.5f, -0.5f, 0, 0xFF, -0.5f, -0.5f, -0.5f, 0, 0xFF, 0, 0, 0, 1, 0
            },
            BlendMode.NULL),
    BLUE(
            "blue",
            FilterType.COLORMATRIX,
            new float[] {0.5f, 0.5f, 0.5f, 0, -255, 0.5f, 0.5f, 0.5f, 0, -170, 0.5f, 0.5f, 0.5f, 0, 0, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    RED(
            "red",
            FilterType.COLORMATRIX,
            new float[] {0.5f, 0.5f, 0.5f, 0, 0, 0.5f, 0.5f, 0.5f, 0, -170, 0.5f, 0.5f, 0.5f, 0, -170, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    GREEN(
            "green",
            FilterType.COLORMATRIX,
            new float[] {0.5f, 0.5f, 0.5f, 0, -170, 0.5f, 0.5f, 0.5f, 0, 0, 0.5f, 0.5f, 0.5f, 0, -170, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    GREEN_2(
            "green_2",
            FilterType.COLORMATRIX,
            new float[] {0.5f, 0.5f, 0.5f, 0, 0, 0.5f, 0.5f, 0.5f, 0, 90, 0.5f, 0.5f, 0.5f, 0, 0, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    YELLOW(
            "Yellow",
            FilterType.COLORMATRIX,
            new float[] {1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0},
            BlendMode.NULL),
    UNKNOWN(
            "UNKNOWN",
            FilterType.COLORMATRIX,
            new float[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            BlendMode.NULL),

    SHADOW("shadow_multiply_02", FilterType.COMPOSITE, new float[0], BlendMode.MULTIPLY),
    HEARTS("hearts_hardlight_02", FilterType.COMPOSITE, new float[0], BlendMode.HARDLIGHT),
    TEXTURE("texture_overlay", FilterType.COMPOSITE, new float[0], BlendMode.OVERLAY),
    PINKY("pinky_nrm", FilterType.COMPOSITE, new float[0], BlendMode.NORMAL),
    STARS("stars_hardlight_02", FilterType.COMPOSITE, new float[0], BlendMode.HARDLIGHT),
    COFFE("coffee_mpl", FilterType.COMPOSITE, new float[0], BlendMode.MULTIPLY),
    SECURITY("security_hardlight", FilterType.COMPOSITE, new float[0], BlendMode.HARDLIGHT),
    BLUEMOOD("bluemood_mpl", FilterType.COMPOSITE, new float[0], BlendMode.MULTIPLY),
    RUSTY("rusty_mpl", FilterType.COMPOSITE, new float[0], BlendMode.MULTIPLY),
    ALIEN("alien_hrd", FilterType.COMPOSITE, new float[0], BlendMode.HARDLIGHT),
    TOXIC("toxic_hrd", FilterType.COMPOSITE, new float[0], BlendMode.HARDLIGHT),
    MISTY("misty_hrd", FilterType.COMPOSITE, new float[0], BlendMode.HARDLIGHT),
    DROPS("drops_mpl", FilterType.COMPOSITE, new float[0], BlendMode.MULTIPLY),
    SHINY("shiny_hrd", FilterType.COMPOSITE, new float[0], BlendMode.HARDLIGHT),
    GLITTER("glitter_hrd", FilterType.COMPOSITE, new float[0], BlendMode.HARDLIGHT),

    FRAME_GOLD("frame_gold", FilterType.FRAME, new float[0], BlendMode.NULL),
    FRAME_GRAY("frame_gray_4", FilterType.FRAME, new float[0], BlendMode.NULL),
    FRAME_BLACK("frame_black_2", FilterType.FRAME, new float[0], BlendMode.NULL),
    FRAME_WOOD("frame_wood_2", FilterType.FRAME, new float[0], BlendMode.NULL),
    FRAME_NRM("finger_nrm", FilterType.FRAME, new float[0], BlendMode.NULL);

    public final String name;
    public final FilterType filterType;
    public final float[] matrix;
    public final BlendMode blendMode;

    CameraFilters(String name, FilterType filterType, float[] matrix, BlendMode blendMode) {
        this.name = name;
        this.filterType = filterType;
        this.matrix = matrix;
        this.blendMode = blendMode;
    }

    public static CameraFilters fromName(String name) {
        for (CameraFilters filter : CameraFilters.values()) {
            if (filter.name.equalsIgnoreCase(name)) {
                return filter;
            }
        }
        return UNKNOWN;
    }

    public enum FilterType {
        COLORMATRIX,
        COMPOSITE,
        FRAME
    }

    public enum BlendMode {
        NULL,
        MULTIPLY,
        HARDLIGHT,
        OVERLAY,
        NORMAL
    }
}
