package com.eu.habbo.imaging.camera;

import com.eu.habbo.imaging.camera.render.CameraMask;
import com.eu.habbo.imaging.camera.render.CameraPlane;
import com.eu.habbo.imaging.camera.render.CameraTexCols;
import com.eu.habbo.imaging.camera.render.JSONCamera;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/** Parses the client's scene JSON and enforces the size limits before anything is drawn. */
public final class CameraParser {
    private static final Gson GSON = new GsonBuilder().create();
    static final int MAX_CORNER_POINTS = 4;
    static final int MAX_MASKS_PER_PLANE = 32;
    static final int MAX_TEXTURE_COLUMNS_PER_PLANE = 64;
    static final int MAX_ASSETS_PER_COLUMN = 16;

    private CameraParser() {}

    public static JSONCamera parse(String json, int maxPlanes, int maxSprites, int maxFilters)
            throws CameraRenderException {
        JSONCamera scene;
        try {
            scene = GSON.fromJson(json, JSONCamera.class);
        } catch (JsonParseException | IllegalStateException | NumberFormatException | StackOverflowError e) {
            throw new CameraRenderException(CameraRenderException.Reason.PARSE_FAILED, "scene JSON is invalid", e);
        }
        if (scene == null) {
            throw new CameraRenderException(CameraRenderException.Reason.PARSE_FAILED, "scene JSON is empty");
        }
        if (scene.getRoomid() < 0) {
            throw new CameraRenderException(CameraRenderException.Reason.PARSE_FAILED, "room id is negative");
        }
        int planes = scene.getPlanes() == null ? 0 : scene.getPlanes().length;
        int sprites = scene.getSprites() == null ? 0 : scene.getSprites().length;
        int filters = scene.getFilters() == null ? 0 : scene.getFilters().length;
        if (planes > maxPlanes) {
            throw limit("planes", planes, maxPlanes);
        }
        if (sprites > maxSprites) {
            throw limit("sprites", sprites, maxSprites);
        }
        if (filters > maxFilters) {
            throw limit("filters", filters, maxFilters);
        }
        if (scene.getPlanes() != null) {
            for (CameraPlane plane : scene.getPlanes()) {
                checkPlane(plane);
            }
        }
        return scene;
    }

    private static void checkPlane(CameraPlane plane) throws CameraRenderException {
        if (plane == null) {
            return;
        }
        if (plane.getCornerPoints() != null && plane.getCornerPoints().length > MAX_CORNER_POINTS) {
            throw limit("plane corner points", plane.getCornerPoints().length, MAX_CORNER_POINTS);
        }
        CameraMask[] masks = plane.getMasks();
        if (masks != null && masks.length > MAX_MASKS_PER_PLANE) {
            throw limit("plane masks", masks.length, MAX_MASKS_PER_PLANE);
        }
        CameraTexCols[] columns = plane.getTexCols();
        if (columns == null) {
            return;
        }
        if (columns.length > MAX_TEXTURE_COLUMNS_PER_PLANE) {
            throw limit("plane texture columns", columns.length, MAX_TEXTURE_COLUMNS_PER_PLANE);
        }
        for (CameraTexCols column : columns) {
            if (column != null
                    && column.getAssetNames() != null
                    && column.getAssetNames().length > MAX_ASSETS_PER_COLUMN) {
                throw limit("texture column assets", column.getAssetNames().length, MAX_ASSETS_PER_COLUMN);
            }
        }
    }

    private static CameraRenderException limit(String what, int count, int max) {
        return new CameraRenderException(
                CameraRenderException.Reason.LIMIT_EXCEEDED, what + " exceeds limit: " + count + " > " + max);
    }
}
