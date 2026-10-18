package com.eu.habbo.imaging.camera.render;

public class CameraPlane {
    private float z;
    private boolean bottomAligned;
    private int color;
    private CameraPositionPoint[] cornerPoints;
    private CameraTexCols[] texCols;
    private CameraMask[] masks;

    public float getZ() {
        return z;
    }

    public boolean isBottomAligned() {
        return bottomAligned;
    }

    public int getColor() {
        return color;
    }

    public CameraPositionPoint[] getCornerPoints() {
        return cornerPoints;
    }

    public CameraTexCols[] getTexCols() {
        return texCols;
    }

    public CameraMask[] getMasks() {
        return masks;
    }
}
