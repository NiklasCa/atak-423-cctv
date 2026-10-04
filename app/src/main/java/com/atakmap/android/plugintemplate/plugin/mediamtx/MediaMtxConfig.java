package com.atakmap.android.plugintemplate.plugin.mediamtx;

import android.content.Context;
import android.content.SharedPreferences;

import com.atakmap.android.preference.AtakPreferences;

import java.net.URI;

/**
 * Configuration holder and persistence for MediaMTX server connection.
 */
public class MediaMtxConfig {

    private static final String PREF_PREFIX = "cctv_mediamtx_";
    public static final String KEY_HOST = PREF_PREFIX + "host";
    public static final String KEY_API_PORT = PREF_PREFIX + "api_port";
    public static final String KEY_RTSP_PORT = PREF_PREFIX + "rtsp_port";
    public static final String KEY_USERNAME = PREF_PREFIX + "username";
    public static final String KEY_PASSWORD = PREF_PREFIX + "password";
    public static final String KEY_POLL_INTERVAL = PREF_PREFIX + "poll_interval";

    public static final int DEFAULT_API_PORT = 9997;
    public static final int DEFAULT_RTSP_PORT = 8554;
    public static final int DEFAULT_POLL_INTERVAL = 10; // seconds

    private String host = "";
    private int apiPort = DEFAULT_API_PORT;
    private int rtspPort = DEFAULT_RTSP_PORT;
    private String username = "";
    private String password = null;
    private int pollIntervalSeconds = DEFAULT_POLL_INTERVAL;

    public MediaMtxConfig() {
    }

    public static MediaMtxConfig load(Context context) {
        MediaMtxConfig config = new MediaMtxConfig();
        if (context == null) {
            return config;
        }

        try {
            AtakPreferences prefs = AtakPreferences.getInstance(context);
            config.host = prefs.get(KEY_HOST, "");
            config.apiPort = prefs.get(KEY_API_PORT, DEFAULT_API_PORT);
            config.rtspPort = prefs.get(KEY_RTSP_PORT, DEFAULT_RTSP_PORT);
            config.username = prefs.get(KEY_USERNAME, "");
            String savedPass = prefs.get(KEY_PASSWORD, null);
            if (savedPass != null && !savedPass.isEmpty()) {
                config.password = savedPass;
            }
            config.pollIntervalSeconds = prefs.get(KEY_POLL_INTERVAL, DEFAULT_POLL_INTERVAL);
        } catch (Throwable t) {
            // Fallback to standard SharedPreferences if AtakPreferences is not available
            SharedPreferences sp = context.getSharedPreferences("mediamtx_cctv_prefs", Context.MODE_PRIVATE);
            config.host = sp.getString(KEY_HOST, "");
            config.apiPort = sp.getInt(KEY_API_PORT, DEFAULT_API_PORT);
            config.rtspPort = sp.getInt(KEY_RTSP_PORT, DEFAULT_RTSP_PORT);
            config.username = sp.getString(KEY_USERNAME, "");
            String spPass = sp.getString(KEY_PASSWORD, null);
            if (spPass != null && !spPass.isEmpty()) {
                config.password = spPass;
            }
            config.pollIntervalSeconds = sp.getInt(KEY_POLL_INTERVAL, DEFAULT_POLL_INTERVAL);
        }

        return config;
    }

    public void save(Context context) {
        if (context == null) {
            return;
        }

        try {
            AtakPreferences prefs = AtakPreferences.getInstance(context);
            prefs.set(KEY_HOST, host);
            prefs.set(KEY_API_PORT, apiPort);
            prefs.set(KEY_RTSP_PORT, rtspPort);
            prefs.set(KEY_USERNAME, username);
            prefs.set(KEY_PASSWORD, getPassword());
            prefs.set(KEY_POLL_INTERVAL, pollIntervalSeconds);
        } catch (Throwable t) {
            SharedPreferences sp = context.getSharedPreferences("mediamtx_cctv_prefs", Context.MODE_PRIVATE);
            sp.edit()
                    .putString(KEY_HOST, host)
                    .putInt(KEY_API_PORT, apiPort)
                    .putInt(KEY_RTSP_PORT, rtspPort)
                    .putString(KEY_USERNAME, username)
                    .putString(KEY_PASSWORD, getPassword())
                    .putInt(KEY_POLL_INTERVAL, pollIntervalSeconds)
                    .apply();
        }
    }

    /**
     * Sanitizes and sets the host address (removes protocol and trailing slashes).
     */
    public void setHost(String rawHost) {
        if (rawHost == null) {
            this.host = "";
            return;
        }
        String h = rawHost.trim();
        if (h.toLowerCase().startsWith("http://")) {
            h = h.substring(7);
        } else if (h.toLowerCase().startsWith("https://")) {
            h = h.substring(8);
        } else if (h.toLowerCase().startsWith("rtsp://")) {
            h = h.substring(7);
        }
        while (h.endsWith("/")) {
            h = h.substring(0, h.length() - 1);
        }
        // If user typed host:port, extract host
        int colonIdx = h.indexOf(':');
        if (colonIdx > 0 && !h.contains("[")) { // skip IPv6 bracketed address
            try {
                int port = Integer.parseInt(h.substring(colonIdx + 1));
                this.apiPort = port;
                h = h.substring(0, colonIdx);
            } catch (NumberFormatException ignored) {
            }
        }
        this.host = h;
    }

    public String getHost() {
        return host != null ? host : "";
    }

    public int getApiPort() {
        return apiPort > 0 ? apiPort : DEFAULT_API_PORT;
    }

    public void setApiPort(int apiPort) {
        this.apiPort = apiPort > 0 ? apiPort : DEFAULT_API_PORT;
    }

    public int getRtspPort() {
        return rtspPort > 0 ? rtspPort : DEFAULT_RTSP_PORT;
    }

    public void setRtspPort(int rtspPort) {
        this.rtspPort = rtspPort > 0 ? rtspPort : DEFAULT_RTSP_PORT;
    }

    public String getUsername() {
        return username != null ? username : "";
    }

    public void setUsername(String username) {
        this.username = username != null ? username.trim() : "";
    }

    public String getPassword() {
        return password != null ? password : "";
    }

    public void setPassword(String password) {
        this.password = (password != null && !password.isEmpty()) ? password : null;
    }

    public int getPollIntervalSeconds() {
        return Math.max(2, pollIntervalSeconds);
    }

    public void setPollIntervalSeconds(int pollIntervalSeconds) {
        this.pollIntervalSeconds = Math.max(2, pollIntervalSeconds);
    }

    public boolean isConfigured() {
        return host != null && !host.trim().isEmpty();
    }

    /**
     * Builds HTTP API URL for the specified path (e.g. "/v3/paths/list").
     */
    public String buildApiUrl(String path) {
        String cleanPath = path != null ? path : "";
        if (!cleanPath.startsWith("/")) {
            cleanPath = "/" + cleanPath;
        }
        return "http://" + getHost() + ":" + getApiPort() + cleanPath;
    }

    /**
     * Builds standard RTSP URL for the given stream name.
     */
    public String buildRtspUrl(String streamName) {
        String cleanStream = streamName != null ? streamName.trim() : "";
        while (cleanStream.startsWith("/")) {
            cleanStream = cleanStream.substring(1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("rtsp://");
        if (username != null && !username.isEmpty()) {
            sb.append(username);
            if (password != null && !password.isEmpty()) {
                sb.append(":").append(password);
            }
            sb.append("@");
        }
        sb.append(getHost()).append(":").append(getRtspPort()).append("/").append(cleanStream);
        return sb.toString();
    }
}
