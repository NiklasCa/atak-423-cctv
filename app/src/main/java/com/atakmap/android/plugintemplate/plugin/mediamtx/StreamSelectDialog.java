package com.atakmap.android.plugintemplate.plugin.mediamtx;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.plugin.PluginTemplate;
import com.atakmap.android.plugintemplate.plugin.R;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.StreamManagementUtils;
import com.atakmap.android.video.manager.VideoManager;
import com.atakmap.coremap.log.Log;

import java.util.List;

/**
 * Dialog presenting available MediaMTX streams for selection on a sensor marker.
 */
public class StreamSelectDialog {

    private static final String TAG = "StreamSelectDialog";
    public static final String VIDEO_DISPLAY_ACTION = "com.atakmap.maps.video.DISPLAY";

    public static void show(final Context context, final MapItem item, final PluginTemplate plugin) {
        if (context == null || item == null) {
            return;
        }

        final MediaMtxManager manager = MediaMtxManager.getInstance();
        final MediaMtxConfig config = manager.getConfig();

        final String callsign = item.getMetaString("callsign", item.getTitle());
        final String displayName = (callsign != null && !callsign.isEmpty()) ? callsign : item.getUID();

        // Check if server is configured
        if (!config.isConfigured()) {
            new AlertDialog.Builder(context)
                    .setTitle("MediaMTX Server Not Configured")
                    .setMessage("Please specify your MediaMTX server host/IP in the CCTV panel settings first.")
                    .setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (plugin != null) {
                                plugin.showPaneForSensor(item);
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        final List<MediaMtxStream> streams = manager.getStreams();

        // Check if no streams are available
        if (streams.isEmpty()) {
            String statusMsg = "No active streams found on MediaMTX server (" + config.getHost() + ").";
            if (manager.getStatus() == MediaMtxManager.Status.ERROR) {
                statusMsg += "\n\nServer Status: " + manager.getLastErrorMessage();
            } else if (manager.getStatus() == MediaMtxManager.Status.CONNECTING) {
                statusMsg += "\n\nStatus: Connecting to server...";
            }

            new AlertDialog.Builder(context)
                    .setTitle("No Streams Available")
                    .setMessage(statusMsg)
                    .setPositiveButton("Refresh", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            manager.refreshNow();
                            Toast.makeText(context, "Refreshing MediaMTX streams...", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNeutralButton("Server Settings", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (plugin != null) {
                                plugin.showPaneForSensor(item);
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // Build list adapter for streams
        Context pluginCtx = (plugin != null && plugin.getPluginContext() != null) ? plugin.getPluginContext() : context;
        final StreamAdapter adapter = new StreamAdapter(context, pluginCtx, streams, item.getMetaString("videoUrl", ""));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Select CCTV Stream for " + displayName);

        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MediaMtxStream selectedStream = streams.get(which);
                assignStreamToSensor(context, item, selectedStream, config, plugin);
            }
        });

        // Add option to clear stream if already configured
        if (item.hasMetaValue("videoUrl")) {
            builder.setNeutralButton("Clear Stream", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    clearStreamFromSensor(context, item, plugin);
                }
            });
        }

        builder.setPositiveButton("Refresh", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                manager.refreshNow();
                Toast.makeText(context, "Refreshing streams...", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    public static void assignStreamToSensor(Context context, MapItem item, MediaMtxStream stream, MediaMtxConfig config, PluginTemplate plugin) {
        if (item == null || stream == null || config == null) {
            return;
        }

        final String callsign = item.getMetaString("callsign", item.getTitle());
        final String displayName = (callsign != null && !callsign.isEmpty()) ? callsign : item.getUID();

        // 1. Create or update ConnectionEntry with full connection details
        ConnectionEntry entry = null;
        String existingUid = item.getMetaString("videoUID", null);
        if (existingUid != null && !existingUid.isEmpty()) {
            try {
                entry = VideoManager.getInstance().getEntry(existingUid);
            } catch (Throwable ignored) {
            }
        }
        if (entry == null) {
            entry = new ConnectionEntry();
        }

        entry.setAlias(stream.getName());
        entry.setProtocol(ConnectionEntry.Protocol.RTSP);

        String host = config.getHost();
        if (config.getUsername() != null && !config.getUsername().isEmpty()) {
            String authHost = config.getUsername();
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                authHost += ":" + config.getPassword();
                entry.setPassphrase(config.getPassword());
            }
            authHost += "@" + host;
            entry.setAddress(authHost);
        } else {
            entry.setAddress(host);
        }

        entry.setPort(config.getRtspPort());

        String path = stream.getName();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        entry.setPath(path);

        entry.setRoverPort(-1);
        entry.setBufferTime(-1);
        entry.setNetworkTimeout(5000);
        entry.setRtspReliable(0);
        entry.setSource(ConnectionEntry.Source.LOCAL_STORAGE);

        // 2. Register ConnectionEntry with ATAK VideoManager so it is available to AddEditAlias and VideoManager
        try {
            VideoManager.getInstance().addEntry(entry);
            Log.d(TAG, "Registered ConnectionEntry in VideoManager with UID: " + entry.getUID() + ", alias: " + entry.getAlias());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to register ConnectionEntry in VideoManager", t);
        }

        // 3. Build the effective stream URL
        String streamUrl = ConnectionEntry.getURL(entry, false);
        if (streamUrl == null || streamUrl.isEmpty()) {
            streamUrl = config.buildRtspUrl(stream.getName());
        }

        Log.d(TAG, "Configuring sensor " + displayName + " with stream URL: " + streamUrl + ", videoUID: " + entry.getUID());

        // 4. Update MapItem metadata - CRITICAL: videoUID must match entry.getUID()
        item.setMetaString("videoUrl", streamUrl);
        item.setMetaString("videoUID", entry.getUID());

        // 5. Persist map item changes
        MapView mapView = MapView.getMapView();
        if (mapView != null) {
            item.persist(mapView.getMapEventDispatcher(), null, StreamSelectDialog.class);
        }

        // 6. Update the CCTV pane if open
        if (plugin != null) {
            plugin.showPaneForSensor(item);
        }

        Toast.makeText(context, "Configured " + displayName + " -> " + stream.getName(), Toast.LENGTH_SHORT).show();

        // 7. Immediately launch the video player (as per user preference)
        launchVideoPlayer(streamUrl, entry.getUID(), displayName);
    }

    public static void clearStreamFromSensor(Context context, MapItem item, PluginTemplate plugin) {
        if (item == null) {
            return;
        }

        String uid = item.getMetaString("videoUID", null);
        if (uid != null && !uid.isEmpty()) {
            try {
                ConnectionEntry entry = VideoManager.getInstance().getEntry(uid);
                if (entry != null) {
                    VideoManager.getInstance().removeEntry(entry);
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to remove ConnectionEntry from VideoManager", t);
            }
        }

        item.removeMetaData("videoUrl");
        item.removeMetaData("videoUID");

        MapView mapView = MapView.getMapView();
        if (mapView != null) {
            item.persist(mapView.getMapEventDispatcher(), null, StreamSelectDialog.class);
        }

        if (plugin != null) {
            plugin.showPaneForSensor(item);
        }

        Toast.makeText(context, "Cleared video stream from sensor", Toast.LENGTH_SHORT).show();
    }

    public static void launchVideoPlayer(String videoUrl, String uid, String callsign) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            return;
        }

        try {
            Intent intent = new Intent(VIDEO_DISPLAY_ACTION);
            intent.putExtra("videoUrl", videoUrl);
            if (uid != null && !uid.isEmpty()) {
                intent.putExtra("uid", uid);
                intent.putExtra("videoUID", uid);
                try {
                    ConnectionEntry entry = VideoManager.getInstance().getEntry(uid);
                    if (entry != null) {
                        intent.putExtra("CONNECTION_ENTRY", entry);
                    }
                } catch (Throwable ignored) {
                }
            }
            if (callsign != null && !callsign.isEmpty()) {
                intent.putExtra("callsign", callsign);
            }

            AtakBroadcast.getInstance().sendBroadcast(intent);
            Log.d(TAG, "Sent video display intent for: " + videoUrl);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to send video display broadcast", t);
        }
    }

    private static class StreamAdapter extends ArrayAdapter<MediaMtxStream> {
        private final Context pluginContext;
        private final String currentVideoUrl;

        public StreamAdapter(Context context, Context pluginContext, List<MediaMtxStream> streams, String currentVideoUrl) {
            super(context, 0, streams);
            this.pluginContext = pluginContext;
            this.currentVideoUrl = currentVideoUrl != null ? currentVideoUrl : "";
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MediaMtxStream stream = getItem(position);
            if (convertView == null) {
                convertView = PluginLayoutInflater.inflate(pluginContext, R.layout.item_stream_entry, parent, false);
            }

            TextView nameView = convertView.findViewById(R.id.stream_name);
            TextView detailsView = convertView.findViewById(R.id.stream_details);
            TextView badgeView = convertView.findViewById(R.id.stream_status_badge);

            if (stream != null) {
                nameView.setText(stream.getName());

                StringBuilder details = new StringBuilder();
                if (!stream.getSourceType().isEmpty()) {
                    details.append(stream.getSourceType());
                }
                if (!stream.getTracks().isEmpty()) {
                    if (details.length() > 0) details.append(" • ");
                    boolean first = true;
                    for (String tr : stream.getTracks()) {
                        if (!first) details.append(", ");
                        details.append(tr);
                        first = false;
                    }
                }
                if (details.length() == 0) {
                    details.append("RTSP Stream");
                }
                detailsView.setText(details.toString());

                if (stream.isReady()) {
                    badgeView.setText("LIVE");
                    badgeView.setTextColor(Color.parseColor("#4caf50"));
                    badgeView.setBackgroundColor(Color.parseColor("#224caf50"));
                } else {
                    badgeView.setText("STANDBY");
                    badgeView.setTextColor(Color.parseColor("#888888"));
                    badgeView.setBackgroundColor(Color.parseColor("#22888888"));
                }
            }

            return convertView;
        }
    }
}
