package com.atakmap.android.plugintemplate.plugin;

import android.content.Context;
import android.widget.Toast;

import com.atakmap.android.maps.MapDataRef;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.android.menu.MapMenuButtonWidget;
import com.atakmap.android.menu.MapMenuFactory;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.menu.MapMenuWidget;
import com.atakmap.android.menu.MenuMapAdapter;
import com.atakmap.android.menu.MenuResourceFactory;
import com.atakmap.android.widgets.WidgetIcon;
import com.atakmap.coremap.log.Log;

import java.io.IOException;

import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.api.widgets.IMapWidget;

/**
 * Custom MapMenuFactory that adds a new, dedicated CCTV button
 * to the radial menu of sensor markers on the map without overriding
 * the existing built-in buttons.
 */
public class SensorRadialMenuFactory implements MapMenuFactory {

    private static final String TAG = "SensorRadialMenuFactory";

    private final Context pluginContext;
    private final PluginTemplate plugin;
    private final MenuResourceFactory defaultResourceFactory;

    public SensorRadialMenuFactory(Context pluginContext, PluginTemplate plugin) {
        this.pluginContext = pluginContext;
        this.plugin = plugin;

        MapView mapView = MapView.getMapView();
        Context atakContext = mapView.getContext();
        MapAssets mapAssets = new MapAssets(atakContext);
        MenuMapAdapter adapter = new MenuMapAdapter();
        try {
            adapter.loadMenuFilters(mapAssets, "filters/menu_filters.xml");
        } catch (IOException e) {
            Log.w(TAG, "Failed to load ATAK menu filters", e);
        }

        this.defaultResourceFactory = new MenuResourceFactory(mapView, mapView.getMapData(), mapAssets, adapter);
    }

    @Override
    public MapMenuWidget create(final MapItem item) {
        if (item == null) {
            return null;
        }

        // Check if item is a sensor marker
        // Standard sensor markers have CoT types starting with "b-m-p-s"
        // (e.g., b-m-p-s-p-loc), or have metadata tags for sensor/camera.
        String type = item.getType();
        boolean isSensor = (type != null && (type.startsWith("b-m-p-s")
                || type.toLowerCase().contains("sensor")
                || type.toLowerCase().contains("camera")))
                || item.hasMetaValue("sensor")
                || item.hasMetaValue("camera")
                || item.hasMetaValue("videoUID");

        if (!isSensor) {
            // Not a sensor marker, let standard ATAK factory create the menu
            return null;
        }

        // Generate the base radial menu widget for this item
        final MapMenuWidget menuWidget = defaultResourceFactory.create(item);
        if (menuWidget == null) {
            return null;
        }

        // Add a brand new, dedicated button to the radial menu
        // We do NOT modify or override any existing buttons (the standard video button is untouched)
        addNewRadialButton(menuWidget, item);

        return menuWidget;
    }

    private void addNewRadialButton(MapMenuWidget menuWidget, final MapItem item) {
        MapView mapView = MapView.getMapView();
        MapMenuButtonWidget newButton = new MapMenuButtonWidget(mapView.getContext());

        // Use ATAK's built-in camera icon to give our CCTV button a clear, distinct visual
        WidgetIcon widgetIcon = new WidgetIcon.Builder()
                .setImageRef(0, MapDataRef.parseUri("asset://icons/camera.png"))
                .setAnchor(16, 16)
                .setSize(32, 32)
                .build();
        newButton.setIcon(widgetIcon);

        // Inherit button background styling and calculate average layout weight from existing buttons
        if (menuWidget.getChildWidgetCount() > 0) {
            float buttonWeight = 0f;
            for (IMapWidget child : menuWidget.getChildren()) {
                if (child instanceof MapMenuButtonWidget) {
                    MapMenuButtonWidget sibling = (MapMenuButtonWidget) child;
                    buttonWeight += sibling.getLayoutWeight();
                    if (newButton.getWidgetBackground() == null && sibling.getWidgetBackground() != null) {
                        newButton.setWidgetBackground(sibling.getWidgetBackground());
                    }
                }
            }
            buttonWeight /= menuWidget.getChildWidgetCount();
            newButton.setLayoutWeight(buttonWeight);
        }

        // Handle clicks on our new radial menu button
        newButton.setOnButtonClickHandler(new IMapMenuButtonWidget.OnButtonClickHandler() {
            @Override
            public boolean isSupported(Object o) {
                return true;
            }

            @Override
            public void performAction(Object o) {
                onSensorRadialButtonClicked(item);
            }
        });

        // Add our new button directly into the radial menu wheel
        menuWidget.addWidget(newButton);
    }

    private void onSensorRadialButtonClicked(final MapItem item) {
        MapView.getMapView().post(new Runnable() {
            @Override
            public void run() {
                // Dismiss radial menu
                MapMenuReceiver menuReceiver = MapMenuReceiver.getInstance();
                if (menuReceiver != null) {
                    menuReceiver.hideMenu();
                }

                String callsign = item.getMetaString("callsign", item.getTitle());
                if (callsign == null || callsign.isEmpty()) {
                    callsign = item.getUID();
                }

                Toast.makeText(MapView.getMapView().getContext(),
                        "Opening CCTV for: " + callsign,
                        Toast.LENGTH_SHORT).show();

                // Open the Drop-Down Pane and update it with this sensor's details
                plugin.showPaneForSensor(item);
            }
        });
    }
}
