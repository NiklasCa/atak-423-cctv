package com.atakmap.android.plugintemplate.plugin;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.plugintemplate.plugin.mediamtx.MediaMtxConfig;
import com.atakmap.android.plugintemplate.plugin.mediamtx.MediaMtxManager;
import com.atakmap.android.plugintemplate.plugin.mediamtx.MediaMtxStream;
import com.atakmap.android.plugintemplate.plugin.mediamtx.StreamSelectDialog;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.List;
import java.util.Locale;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

public class PluginTemplate implements IPlugin, MediaMtxManager.StreamsListener {

    private IServiceController serviceController;
    private Context pluginContext;
    private IHostUIService uiService;
    private ToolbarItem toolbarItem;
    private Pane templatePane;
    private View paneView;

    private SensorRadialMenuFactory radialMenuFactory;
    private MapItem currentSelectedSensor;

    // MediaMTX UI View references
    private EditText mediamtxHostEdit;
    private EditText mediamtxApiPortEdit;
    private EditText mediamtxRtspPortEdit;
    private EditText mediamtxUserEdit;
    private EditText mediamtxPassEdit;
    private EditText mediamtxPollIntervalEdit;
    private TextView mediamtxStatusBadge;
    private TextView mediamtxStatusDetail;
    private TextView mediamtxStreamsSummary;
    private Button btnSaveMediaMtx;
    private Button btnRefreshMediaMtx;

    // Sensor UI View references
    private TextView sensorTitleText;
    private TextView sensorCallsignText;
    private TextView sensorUidText;
    private TextView sensorCoordsText;
    private TextView sensorStreamUrlText;
    private TextView sensorStatusText;
    private Button btnAssignSensorStream;
    private Button btnPlaySensorStream;
    private Button btnClearSensorStream;

    public PluginTemplate(IServiceController serviceController) {
        this.serviceController = serviceController;
        final PluginContextProvider ctxProvider = serviceController
                .getService(PluginContextProvider.class);
        if (ctxProvider != null) {
            pluginContext = ctxProvider.getPluginContext();
            pluginContext.setTheme(R.style.ATAKPluginTheme);
        }

        // obtain the UI service
        uiService = serviceController.getService(IHostUIService.class);

        // create the toolbar button for the Tool Tray
        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        showPane();
                    }
                }).setIdentifier(pluginContext.getPackageName())
                .build();

        // create the radial menu factory for sensor markers
        radialMenuFactory = new SensorRadialMenuFactory(pluginContext, this);

        // Initialize MediaMTX background manager
        MediaMtxManager.getInstance().initialize(pluginContext != null ? pluginContext : MapView.getMapView().getContext());
        MediaMtxManager.getInstance().addListener(this);
    }

    public Context getPluginContext() {
        return pluginContext;
    }

    @Override
    public void onStart() {
        // Add plugin to the Tool Tray
        if (uiService != null) {
            uiService.addToolbarItem(toolbarItem);
        }

        // Register radial menu factory for sensor markers
        MapMenuReceiver menuReceiver = MapMenuReceiver.getInstance();
        if (menuReceiver != null && radialMenuFactory != null) {
            menuReceiver.registerMapMenuFactory(radialMenuFactory);
        }

        // Start MediaMTX background polling
        Context ctx = pluginContext != null ? pluginContext : (MapView.getMapView() != null ? MapView.getMapView().getContext() : null);
        if (ctx != null) {
            MediaMtxManager.getInstance().startPolling(ctx);
        }
    }

    @Override
    public void onStop() {
        // Stop MediaMTX background polling and unregister listeners
        MediaMtxManager.getInstance().removeListener(this);
        MediaMtxManager.getInstance().dispose();

        // Remove button from Tool Tray
        if (uiService != null) {
            uiService.removeToolbarItem(toolbarItem);
        }

        // Unregister radial menu factory
        MapMenuReceiver menuReceiver = MapMenuReceiver.getInstance();
        if (menuReceiver != null && radialMenuFactory != null) {
            menuReceiver.unregisterMapMenuFactory(radialMenuFactory);
        }
    }

    private void ensurePane() {
        if (templatePane == null) {
            paneView = PluginLayoutInflater.inflate(pluginContext, R.layout.main_layout, null);

            // Bind MediaMTX configuration views
            mediamtxHostEdit = paneView.findViewById(R.id.mediamtx_host_edit);
            mediamtxApiPortEdit = paneView.findViewById(R.id.mediamtx_api_port_edit);
            mediamtxRtspPortEdit = paneView.findViewById(R.id.mediamtx_rtsp_port_edit);
            mediamtxUserEdit = paneView.findViewById(R.id.mediamtx_user_edit);
            mediamtxPassEdit = paneView.findViewById(R.id.mediamtx_pass_edit);
            mediamtxPollIntervalEdit = paneView.findViewById(R.id.mediamtx_poll_interval_edit);
            mediamtxStatusBadge = paneView.findViewById(R.id.mediamtx_status_badge);
            mediamtxStatusDetail = paneView.findViewById(R.id.mediamtx_status_detail);
            mediamtxStreamsSummary = paneView.findViewById(R.id.mediamtx_streams_summary);
            btnSaveMediaMtx = paneView.findViewById(R.id.btn_save_mediamtx);
            btnRefreshMediaMtx = paneView.findViewById(R.id.btn_refresh_mediamtx);

            // Populate current MediaMTX config values into UI
            MediaMtxConfig config = MediaMtxManager.getInstance().getConfig();
            if (config != null) {
                if (mediamtxHostEdit != null) mediamtxHostEdit.setText(config.getHost());
                if (mediamtxApiPortEdit != null) mediamtxApiPortEdit.setText(String.valueOf(config.getApiPort()));
                if (mediamtxRtspPortEdit != null) mediamtxRtspPortEdit.setText(String.valueOf(config.getRtspPort()));
                if (mediamtxUserEdit != null) mediamtxUserEdit.setText(config.getUsername());
                if (mediamtxPassEdit != null) mediamtxPassEdit.setText(config.getPassword());
                if (mediamtxPollIntervalEdit != null) mediamtxPollIntervalEdit.setText(String.valueOf(config.getPollIntervalSeconds()));
            }

            if (btnSaveMediaMtx != null) {
                btnSaveMediaMtx.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        saveMediaMtxSettingsFromUi();
                    }
                });
            }

            if (btnRefreshMediaMtx != null) {
                btnRefreshMediaMtx.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        MediaMtxManager.getInstance().refreshNow();
                        Toast.makeText(pluginContext, "Refreshing MediaMTX streams...", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Bind Sensor views
            sensorTitleText = paneView.findViewById(R.id.sensor_title);
            sensorCallsignText = paneView.findViewById(R.id.sensor_callsign_value);
            sensorUidText = paneView.findViewById(R.id.sensor_uid_value);
            sensorCoordsText = paneView.findViewById(R.id.sensor_coords_value);
            sensorStreamUrlText = paneView.findViewById(R.id.sensor_stream_url_value);
            if (sensorStreamUrlText != null) {
                sensorStreamUrlText.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (currentSelectedSensor != null) {
                            String uid = currentSelectedSensor.getMetaString("videoUID", null);
                            com.atakmap.android.video.ConnectionEntry entry = null;
                            if (uid != null && !uid.isEmpty()) {
                                try {
                                    entry = com.atakmap.android.video.manager.VideoManager.getInstance().getEntry(uid);
                                } catch (Throwable ignored) {
                                }
                            }
                            Context ctx = MapView.getMapView() != null ? MapView.getMapView().getContext() : pluginContext;
                            new com.atakmap.android.video.AddEditAlias(ctx).addEditConnection(entry, new com.atakmap.android.video.AddEditAlias.AliasModifiedListener() {
                                @Override
                                public void aliasModified(com.atakmap.android.video.ConnectionEntry modified) {
                                    if (modified != null) {
                                        currentSelectedSensor.setMetaString("videoUrl", com.atakmap.android.video.ConnectionEntry.getURL(modified, false));
                                        currentSelectedSensor.setMetaString("videoUID", modified.getUID());
                                        MapView mv = MapView.getMapView();
                                        if (mv != null) {
                                            currentSelectedSensor.persist(mv.getMapEventDispatcher(), null, PluginTemplate.class);
                                        }
                                        showPaneForSensor(currentSelectedSensor);
                                    }
                                }
                            });
                        }
                    }
                });
            }
            sensorStatusText = paneView.findViewById(R.id.sensor_status_value);

            btnAssignSensorStream = paneView.findViewById(R.id.btn_assign_sensor_stream);
            if (btnAssignSensorStream != null) {
                btnAssignSensorStream.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (currentSelectedSensor != null) {
                            Context ctx = MapView.getMapView() != null ? MapView.getMapView().getContext() : pluginContext;
                            StreamSelectDialog.show(ctx, currentSelectedSensor, PluginTemplate.this);
                        } else {
                            Toast.makeText(pluginContext, "Please select a sensor marker first.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            btnPlaySensorStream = paneView.findViewById(R.id.btn_connect_stream);
            if (btnPlaySensorStream != null) {
                btnPlaySensorStream.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (currentSelectedSensor != null && currentSelectedSensor.hasMetaValue("videoUrl")) {
                            String url = currentSelectedSensor.getMetaString("videoUrl", "");
                            String uid = currentSelectedSensor.getMetaString("videoUID", "");
                            String callsign = currentSelectedSensor.getMetaString("callsign", currentSelectedSensor.getTitle());
                            StreamSelectDialog.launchVideoPlayer(url, uid, callsign);
                        } else {
                            Toast.makeText(pluginContext, "No video stream configured for this sensor.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            btnClearSensorStream = paneView.findViewById(R.id.btn_clear_sensor_stream);
            if (btnClearSensorStream != null) {
                btnClearSensorStream.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (currentSelectedSensor != null) {
                            Context ctx = MapView.getMapView() != null ? MapView.getMapView().getContext() : pluginContext;
                            StreamSelectDialog.clearStreamFromSensor(ctx, currentSelectedSensor, PluginTemplate.this);
                        }
                    }
                });
            }

            // Update UI with initial streams state
            updateMediaMtxUi(MediaMtxManager.getInstance().getStreams(),
                    MediaMtxManager.getInstance().getStatus(),
                    MediaMtxManager.getInstance().getLastErrorMessage());

            templatePane = new PaneBuilder(paneView)
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.6D)
                    .build();
        }
    }

    private void saveMediaMtxSettingsFromUi() {
        if (paneView == null) return;

        MediaMtxConfig config = new MediaMtxConfig();

        if (mediamtxHostEdit != null) {
            config.setHost(mediamtxHostEdit.getText().toString());
        }

        if (mediamtxApiPortEdit != null) {
            try {
                config.setApiPort(Integer.parseInt(mediamtxApiPortEdit.getText().toString().trim()));
            } catch (NumberFormatException e) {
                config.setApiPort(MediaMtxConfig.DEFAULT_API_PORT);
            }
        }

        if (mediamtxRtspPortEdit != null) {
            try {
                config.setRtspPort(Integer.parseInt(mediamtxRtspPortEdit.getText().toString().trim()));
            } catch (NumberFormatException e) {
                config.setRtspPort(MediaMtxConfig.DEFAULT_RTSP_PORT);
            }
        }

        if (mediamtxUserEdit != null) {
            config.setUsername(mediamtxUserEdit.getText().toString());
        }

        if (mediamtxPassEdit != null) {
            config.setPassword(mediamtxPassEdit.getText().toString());
        }

        if (mediamtxPollIntervalEdit != null) {
            try {
                config.setPollIntervalSeconds(Integer.parseInt(mediamtxPollIntervalEdit.getText().toString().trim()));
            } catch (NumberFormatException e) {
                config.setPollIntervalSeconds(MediaMtxConfig.DEFAULT_POLL_INTERVAL);
            }
        }

        Context ctx = pluginContext != null ? pluginContext : (MapView.getMapView() != null ? MapView.getMapView().getContext() : null);
        MediaMtxManager.getInstance().updateConfig(config, ctx);

        Toast.makeText(pluginContext, "MediaMTX settings saved", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStreamsUpdated(final List<MediaMtxStream> streams, final MediaMtxManager.Status status, final String errorMessage) {
        if (paneView == null) {
            return;
        }

        updateMediaMtxUi(streams, status, errorMessage);
    }

    private void updateMediaMtxUi(List<MediaMtxStream> streams, MediaMtxManager.Status status, String errorMessage) {
        if (mediamtxStatusBadge == null) {
            return;
        }

        switch (status) {
            case CONNECTED:
                mediamtxStatusBadge.setText("CONNECTED");
                mediamtxStatusBadge.setTextColor(Color.parseColor("#4caf50"));
                mediamtxStatusBadge.setBackgroundColor(Color.parseColor("#224caf50"));
                if (mediamtxStatusDetail != null) {
                    mediamtxStatusDetail.setText(streams.size() + " active stream(s) available on server.");
                    mediamtxStatusDetail.setTextColor(Color.parseColor("#aaaaaa"));
                }
                break;
            case CONNECTING:
                mediamtxStatusBadge.setText("CONNECTING");
                mediamtxStatusBadge.setTextColor(Color.parseColor("#ffb300"));
                mediamtxStatusBadge.setBackgroundColor(Color.parseColor("#22ffb300"));
                if (mediamtxStatusDetail != null) {
                    mediamtxStatusDetail.setText("Polling server for streams...");
                    mediamtxStatusDetail.setTextColor(Color.parseColor("#aaaaaa"));
                }
                break;
            case ERROR:
                mediamtxStatusBadge.setText("ERROR");
                mediamtxStatusBadge.setTextColor(Color.parseColor("#f44336"));
                mediamtxStatusBadge.setBackgroundColor(Color.parseColor("#22f44336"));
                if (mediamtxStatusDetail != null) {
                    mediamtxStatusDetail.setText(errorMessage != null && !errorMessage.isEmpty() ? errorMessage : "Connection failed");
                    mediamtxStatusDetail.setTextColor(Color.parseColor("#f44336"));
                }
                break;
            case UNCONFIGURED:
            default:
                mediamtxStatusBadge.setText("UNCONFIGURED");
                mediamtxStatusBadge.setTextColor(Color.parseColor("#888888"));
                mediamtxStatusBadge.setBackgroundColor(Color.parseColor("#22888888"));
                if (mediamtxStatusDetail != null) {
                    mediamtxStatusDetail.setText("Specify server IP and ports to fetch available streams.");
                    mediamtxStatusDetail.setTextColor(Color.parseColor("#888888"));
                }
                break;
        }

        if (mediamtxStreamsSummary != null) {
            if (streams.isEmpty()) {
                mediamtxStreamsSummary.setText(status == MediaMtxManager.Status.CONNECTED ?
                        "Connected to MediaMTX, but no streams are currently registered." :
                        "No streams discovered yet.");
                mediamtxStreamsSummary.setTextColor(Color.parseColor("#888888"));
            } else {
                StringBuilder sb = new StringBuilder("Available Streams (" + streams.size() + "): ");
                boolean first = true;
                for (MediaMtxStream s : streams) {
                    if (!first) sb.append(", ");
                    sb.append(s.getName()).append(s.isReady() ? " [LIVE]" : " [STANDBY]");
                    first = false;
                }
                mediamtxStreamsSummary.setText(sb.toString());
                mediamtxStreamsSummary.setTextColor(Color.parseColor("#00e5ff"));
            }
        }

        // Sync UI inputs if updated from an external preference file / Data Package
        MediaMtxConfig cfg = MediaMtxManager.getInstance().getConfig();
        if (cfg != null) {
            if (mediamtxHostEdit != null && !mediamtxHostEdit.hasFocus()) {
                mediamtxHostEdit.setText(cfg.getHost());
            }
            if (mediamtxApiPortEdit != null && !mediamtxApiPortEdit.hasFocus()) {
                mediamtxApiPortEdit.setText(String.valueOf(cfg.getApiPort()));
            }
            if (mediamtxRtspPortEdit != null && !mediamtxRtspPortEdit.hasFocus()) {
                mediamtxRtspPortEdit.setText(String.valueOf(cfg.getRtspPort()));
            }
            if (mediamtxUserEdit != null && !mediamtxUserEdit.hasFocus()) {
                mediamtxUserEdit.setText(cfg.getUsername());
            }
            if (mediamtxPassEdit != null && !mediamtxPassEdit.hasFocus()) {
                mediamtxPassEdit.setText(cfg.getPassword());
            }
            if (mediamtxPollIntervalEdit != null && !mediamtxPollIntervalEdit.hasFocus()) {
                mediamtxPollIntervalEdit.setText(String.valueOf(cfg.getPollIntervalSeconds()));
            }
        }
    }

    public void showPane() {
        ensurePane();
        if (uiService != null && !uiService.isPaneVisible(templatePane)) {
            uiService.showPane(templatePane, null);
        }
    }

    public void showPaneForSensor(MapItem item) {
        ensurePane();
        this.currentSelectedSensor = item;

        if (item != null) {
            String callsign = item.getMetaString("callsign", item.getTitle());
            if (callsign == null || callsign.isEmpty()) {
                callsign = item.getUID();
            }

            if (sensorTitleText != null) {
                sensorTitleText.setText(callsign);
            }
            if (sensorCallsignText != null) {
                sensorCallsignText.setText(callsign);
            }
            if (sensorUidText != null) {
                sensorUidText.setText(item.getUID());
            }

            if (sensorCoordsText != null) {
                if (item instanceof PointMapItem) {
                    GeoPoint point = ((PointMapItem) item).getPoint();
                    if (point != null) {
                        sensorCoordsText.setText(String.format(Locale.US, "%.5f, %.5f", point.getLatitude(), point.getLongitude()));
                    } else {
                        sensorCoordsText.setText("--");
                    }
                } else {
                    sensorCoordsText.setText("--");
                }
            }

            String videoUrl = item.getMetaString("videoUrl", null);
            if (sensorStreamUrlText != null) {
                if (videoUrl != null && !videoUrl.isEmpty()) {
                    sensorStreamUrlText.setText(videoUrl);
                    sensorStreamUrlText.setTextColor(Color.parseColor("#4caf50"));
                } else {
                    sensorStreamUrlText.setText("None configured");
                    sensorStreamUrlText.setTextColor(Color.parseColor("#ffb300"));
                }
            }

            if (sensorStatusText != null) {
                sensorStatusText.setText(videoUrl != null ? "Stream Assigned" : "Selected");
            }
        }

        if (uiService != null && !uiService.isPaneVisible(templatePane)) {
            uiService.showPane(templatePane, null);
        }
    }
}
