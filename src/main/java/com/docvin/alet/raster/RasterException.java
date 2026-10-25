package com.docvin.alet.raster;

import java.io.IOException;

public final class RasterException extends IOException {
    public final String key;
    public final Object[] arguments;
    public RasterException(String key, Object... arguments) {
        super(key);
        this.key = key;
        this.arguments = arguments;
    }
}
