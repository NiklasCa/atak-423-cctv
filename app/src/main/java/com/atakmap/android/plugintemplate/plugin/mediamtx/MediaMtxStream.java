package com.atakmap.android.plugintemplate.plugin.mediamtx;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a stream/path retrieved from MediaMTX API.
 */
public class MediaMtxStream implements Serializable {

    private final String name;
    private final boolean ready;
    private final String sourceType;
    private final List<String> tracks;
    private final long bytesReceived;
    private final String readyTime;

    public MediaMtxStream(String name, boolean ready, String sourceType, List<String> tracks, long bytesReceived, String readyTime) {
        this.name = name != null ? name : "";
        this.ready = ready;
        this.sourceType = sourceType != null ? sourceType : "";
        this.tracks = tracks != null ? new ArrayList<>(tracks) : Collections.<String>emptyList();
        this.bytesReceived = bytesReceived;
        this.readyTime = readyTime != null ? readyTime : "";
    }

    public String getName() {
        return name;
    }

    public boolean isReady() {
        return ready;
    }

    public String getSourceType() {
        return sourceType;
    }

    public List<String> getTracks() {
        return Collections.unmodifiableList(tracks);
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public String getReadyTime() {
        return readyTime;
    }

    public String getStatusBadge() {
        return ready ? "LIVE" : "STANDBY";
    }

    @Override
    public String toString() {
        return name + (ready ? " [LIVE]" : " [STANDBY]");
    }
}
