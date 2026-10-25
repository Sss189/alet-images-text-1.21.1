package com.docvin.alet.raster;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JobControl {
    public final AtomicBoolean cancelled = new AtomicBoolean();
    public volatile int percent;
    public volatile String phase = "loading";
    public void check() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
}
