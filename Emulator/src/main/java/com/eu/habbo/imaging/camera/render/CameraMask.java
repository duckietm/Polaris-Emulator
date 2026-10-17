package com.eu.habbo.imaging.camera.render;

public class CameraMask {
    private String name;
    private boolean flipH;
    private boolean flipV;
    private CameraPositionPoint location;

    public String getName() {
        return name;
    }

    public boolean isFlipH() {
        return flipH;
    }

    public boolean isFlipV() {
        return flipV;
    }

    public CameraPositionPoint getLocation() {
        return location;
    }
}
