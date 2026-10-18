package com.eu.habbo.imaging.camera.render;

import java.awt.Point;

public class CameraPositionPoint extends Point {
    public CameraPositionPoint(int x, int y) {
        super(x, y);
    }

    @Override
    public String toString() {
        return "CameraPositionPoint: x " + this.x + ",y " + this.y;
    }
}
