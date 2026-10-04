package com.atakmap.android.plugintemplate.plugin.mediamtx;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

import android.content.SharedPreferences;
import com.atakmap.android.preference.AtakPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages background polling of the MediaMTX server API,
 * parsing available streams, and notifying registered listeners.
 */
public class MediaMtxManager {

    private static final String TAG = "MediaMtxManager";

    public enum Status {
        UNCONFIGURED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    public interface StreamsListener {
        void onStreamsUpdated(List<MediaMtxStream> streams, Status status, String errorMessage);
    }

    private static MediaMtxManager instance;

    private Context appContext;
    private SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener;

    private MediaMtxConfig config;
    private Status currentStatus = Status.UNCONFIGURED;
    private String lastErrorMessage = "";
    private long lastPollTimestamp = 0;

    private final List<MediaMtxStream> cachedStreams = new CopyOnWriteArrayList<>();
    private final List<StreamsListener> listeners = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> pollingTask;
    private boolean isPolling = false;

    private MediaMtxManager() {
        this.config = new MediaMtxConfig();
    }

    public static synchronized MediaMtxManager getInstance() {
        if (instance == null) {
            instance = new MediaMtxManager();
        }
        return instance;
    }

    public synchronized void initialize(Context context) {
        this.appContext = context != null ? context.getApplicationContext() : null;
        this.config = MediaMtxConfig.load(context);
        if (config.isConfigured()) {
            this.currentStatus = Status.CONNECTING;
        } else {
            this.currentStatus = Status.UNCONFIGURED;
        }

        // Register listener for preference changes (e.g., when a Data Package .pref is imported)
        registerPreferenceListener(context);

        startPolling(context);
    }

    private void registerPreferenceListener(Context context) {
        if (context == null || prefChangeListener != null) {
            return;
        }
        try {
            SharedPreferences sp = AtakPreferences.getInstance(context).getSharedPrefs();
            if (sp != null) {
                prefChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                    @Override
                    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                        if (key != null && key.startsWith("cctv_mediamtx_")) {
                            onExternalPreferencesChanged();
                        }
                    }
                };
                sp.registerOnSharedPreferenceChangeListener(prefChangeListener);
                Log.d(TAG, "Registered OnSharedPreferenceChangeListener for MediaMTX settings");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to register preference change listener", t);
        }
    }

    private synchronized void onExternalPreferencesChanged() {
        Context ctx = appContext != null ? appContext : (MapView.getMapView() != null ? MapView.getMapView().getContext() : null);
        if (ctx == null) {
            return;
        }
        MediaMtxConfig newConfig = MediaMtxConfig.load(ctx);
        if (!newConfig.getHost().equals(config.getHost())
                || newConfig.getApiPort() != config.getApiPort()
                || newConfig.getRtspPort() != config.getRtspPort()
                || !newConfig.getUsername().equals(config.getUsername())
                || !newConfig.getPassword().equals(config.getPassword())
                || newConfig.getPollIntervalSeconds() != config.getPollIntervalSeconds()) {
            Log.d(TAG, "MediaMTX preferences updated externally: host=" + newConfig.getHost());
            this.config = newConfig;
            stopPolling();
            if (config.isConfigured()) {
                currentStatus = Status.CONNECTING;
                startPolling(ctx);
                refreshNow();
            } else {
                currentStatus = Status.UNCONFIGURED;
                cachedStreams.clear();
                notifyListeners();
            }
        }
    }

    public synchronized void updateConfig(MediaMtxConfig newConfig, Context context) {
        this.config = newConfig;
        this.config.save(context);

        if (!config.isConfigured()) {
            currentStatus = Status.UNCONFIGURED;
            cachedStreams.clear();
            notifyListeners();
            stopPolling();
            return;
        }

        // Restart polling with new interval/host
        stopPolling();
        startPolling(context);
        refreshNow();
    }

    public synchronized void startPolling(Context context) {
        if (isPolling) {
            return;
        }

        if (!config.isConfigured()) {
            currentStatus = Status.UNCONFIGURED;
            notifyListeners();
            return;
        }

        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadScheduledExecutor();
        }

        isPolling = true;
        int interval = config.getPollIntervalSeconds();

        pollingTask = executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    pollServer();
                } catch (Throwable t) {
                    Log.e(TAG, "Unexpected error in poll task", t);
                }
            }
        }, 0, interval, TimeUnit.SECONDS);

        Log.d(TAG, "MediaMTX background polling started (every " + interval + "s)");
    }

    public synchronized void stopPolling() {
        if (pollingTask != null) {
            pollingTask.cancel(true);
            pollingTask = null;
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            executorService = null;
        }
        isPolling = false;
        Log.d(TAG, "MediaMTX background polling stopped");
    }

    public synchronized void dispose() {
        stopPolling();
        if (prefChangeListener != null && appContext != null) {
            try {
                SharedPreferences sp = AtakPreferences.getInstance(appContext).getSharedPrefs();
                if (sp != null) {
                    sp.unregisterOnSharedPreferenceChangeListener(prefChangeListener);
                }
            } catch (Throwable ignored) {
            }
            prefChangeListener = null;
        }
    }

    public void refreshNow() {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadScheduledExecutor();
        }
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                pollServer();
            }
        });
    }

    private void pollServer() {
        if (!config.isConfigured()) {
            currentStatus = Status.UNCONFIGURED;
            notifyListeners();
            return;
        }

        Log.d(TAG, "Polling MediaMTX at " + config.getHost() + ":" + config.getApiPort());

        List<MediaMtxStream> fetchedStreams = null;
        String errorMsg = null;

        // Try v3 paths first, then fallback to v2 or v1 if 404 is returned
        String[] endpoints = new String[] { "/v3/paths/list", "/v2/paths/list", "/v1/paths/list" };

        for (String endpoint : endpoints) {
            try {
                String response = executeHttpGet(config.buildApiUrl(endpoint));
                if (response != null) {
                    fetchedStreams = parseStreamsJson(response);
                    break;
                }
            } catch (HttpNotFoundException e) {
                // Endpoint not found on this MediaMTX version, try next version
                Log.d(TAG, "Endpoint " + endpoint + " returned 404, trying fallback...");
            } catch (Exception e) {
                Log.w(TAG, "Error connecting to MediaMTX on " + endpoint + ": " + e.getMessage());
                errorMsg = e.getMessage();
                // Network or connection error, no need to retry other endpoints immediately
                break;
            }
        }

        if (fetchedStreams != null) {
            cachedStreams.clear();
            cachedStreams.addAll(fetchedStreams);
            currentStatus = Status.CONNECTED;
            lastErrorMessage = "";
            lastPollTimestamp = System.currentTimeMillis();
            Log.d(TAG, "Successfully fetched " + fetchedStreams.size() + " streams from MediaMTX");
        } else {
            currentStatus = Status.ERROR;
            lastErrorMessage = errorMsg != null ? errorMsg : "MediaMTX paths API not accessible";
            Log.w(TAG, "MediaMTX poll failed: " + lastErrorMessage);
        }

        notifyListeners();
    }

    private String executeHttpGet(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Accept", "application/json");

        // Basic Authentication if credentials are provided
        if (!config.getUsername().isEmpty()) {
            String credentials = config.getUsername() + ":" + config.getPassword();
            String authHeader = "Basic " + Base64.encodeToString(credentials.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            conn.setRequestProperty("Authorization", authHeader);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 404) {
            conn.disconnect();
            throw new HttpNotFoundException("404 Not Found");
        }

        if (responseCode == 401 || responseCode == 403) {
            conn.disconnect();
            throw new Exception("Authentication failed (HTTP " + responseCode + ")");
        }

        if (responseCode != 200) {
            conn.disconnect();
            throw new Exception("Server returned HTTP " + responseCode);
        }

        InputStream is = conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        conn.disconnect();

        return sb.toString();
    }

    private List<MediaMtxStream> parseStreamsJson(String jsonStr) {
        List<MediaMtxStream> results = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray itemsArray = root.optJSONArray("items");
            if (itemsArray == null) {
                // Check if the root itself is an array
                return results;
            }

            for (int i = 0; i < itemsArray.length(); i++) {
                JSONObject item = itemsArray.optJSONObject(i);
                if (item == null) continue;

                String name = item.optString("name", "");
                boolean ready = item.optBoolean("ready", false);
                long bytesReceived = item.optLong("bytesReceived", 0);
                String readyTime = item.optString("readyTime", "");

                String sourceType = "";
                JSONObject sourceObj = item.optJSONObject("source");
                if (sourceObj != null) {
                    sourceType = sourceObj.optString("type", "");
                }

                List<String> tracks = new ArrayList<>();
                JSONArray tracksArr = item.optJSONArray("tracks");
                if (tracksArr != null) {
                    for (int t = 0; t < tracksArr.length(); t++) {
                        tracks.add(tracksArr.optString(t));
                    }
                }

                results.add(new MediaMtxStream(name, ready, sourceType, tracks, bytesReceived, readyTime));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing MediaMTX streams JSON", e);
        }
        return results;
    }

    private void notifyListeners() {
        final List<MediaMtxStream> currentStreams = Collections.unmodifiableList(new ArrayList<>(cachedStreams));
        final Status status = currentStatus;
        final String error = lastErrorMessage;

        Runnable callback = new Runnable() {
            @Override
            public void run() {
                for (StreamsListener listener : listeners) {
                    try {
                        listener.onStreamsUpdated(currentStreams, status, error);
                    } catch (Throwable t) {
                        Log.e(TAG, "Error notifying stream listener", t);
                    }
                }
            }
        };

        MapView mv = MapView.getMapView();
        if (mv != null) {
            mv.post(callback);
        } else {
            new Handler(Looper.getMainLooper()).post(callback);
        }
    }

    public void addListener(StreamsListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            // Immediately notify listener of current state
            listener.onStreamsUpdated(Collections.unmodifiableList(new ArrayList<>(cachedStreams)), currentStatus, lastErrorMessage);
        }
    }

    public void removeListener(StreamsListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public MediaMtxConfig getConfig() {
        return config;
    }

    public List<MediaMtxStream> getStreams() {
        return Collections.unmodifiableList(new ArrayList<>(cachedStreams));
    }

    public Status getStatus() {
        return currentStatus;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public long getLastPollTimestamp() {
        return lastPollTimestamp;
    }

    private static class HttpNotFoundException extends Exception {
        public HttpNotFoundException(String message) {
            super(message);
        }
    }
}
