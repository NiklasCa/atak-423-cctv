package com.atakmap.android.plugintemplate.plugin;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.Locale;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

public class PluginTemplate implements IPlugin {

    IServiceController serviceController;
    Context pluginContext;
    IHostUIService uiService;
    ToolbarItem toolbarItem;
    Pane templatePane;
    View paneView;

    SensorRadialMenuFactory radialMenuFactory;

    // UI View references inside paneView
    TextView sensorTitleText;
    TextView sensorCallsignText;
    TextView sensorUidText;
    TextView sensorCoordsText;
    TextView sensorStatusText;

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
    }

    @Override
    public void onStop() {
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

            sensorTitleText = paneView.findViewById(R.id.sensor_title);
            sensorCallsignText = paneView.findViewById(R.id.sensor_callsign_value);
            sensorUidText = paneView.findViewById(R.id.sensor_uid_value);
            sensorCoordsText = paneView.findViewById(R.id.sensor_coords_value);
            sensorStatusText = paneView.findViewById(R.id.sensor_status_value);

            Button btnConnect = paneView.findViewById(R.id.btn_connect_stream);
            if (btnConnect != null) {
                btnConnect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(pluginContext, "Connect Stream clicked (UI placeholder)", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            Button btnSnapshot = paneView.findViewById(R.id.btn_snapshot);
            if (btnSnapshot != null) {
                btnSnapshot.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(pluginContext, "Take Snapshot clicked (UI placeholder)", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            templatePane = new PaneBuilder(paneView)
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();
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

            if (sensorStatusText != null) {
                sensorStatusText.setText("Selected");
            }
        }

        if (uiService != null && !uiService.isPaneVisible(templatePane)) {
            uiService.showPane(templatePane, null);
        }
    }
}
