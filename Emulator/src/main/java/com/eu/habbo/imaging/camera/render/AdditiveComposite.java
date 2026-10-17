package com.eu.habbo.imaging.camera.render;

import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;

public class AdditiveComposite implements Composite {
    public AdditiveComposite() {
        super();
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
        return new AdditiveCompositeContext();
    }
}
