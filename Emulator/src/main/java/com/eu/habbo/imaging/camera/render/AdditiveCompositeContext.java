package com.eu.habbo.imaging.camera.render;

import java.awt.CompositeContext;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

public class AdditiveCompositeContext implements CompositeContext {
    public AdditiveCompositeContext() {}

    @Override
    public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
        int chan1 = src.getNumBands();
        int chan2 = dstIn.getNumBands();
        int minCh = Math.min(chan1, chan2);

        for (int x = 0; x < dstIn.getWidth(); x++) {
            for (int y = 0; y < dstIn.getHeight(); y++) {
                float[] pxSrc = null;
                pxSrc = src.getPixel(x, y, pxSrc);
                float[] pxDst = null;
                pxDst = dstIn.getPixel(x, y, pxDst);

                float alpha = 255;
                if (pxSrc.length > 3) {
                    alpha = pxSrc[3] / 2;
                }

                for (int i = 0; i < 3 && i < minCh; i++) {
                    pxDst[i] = Math.min(255, (pxSrc[i] * (alpha / 255)) - (1 / 255f) + (pxDst[i]));
                    dstOut.setPixel(x, y, pxDst);
                }
            }
        }
    }

    @Override
    public void dispose() {}
}
