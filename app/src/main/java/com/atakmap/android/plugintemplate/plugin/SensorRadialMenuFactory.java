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
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.WidgetIcon;
import com.atakmap.coremap.log.Log;

import java.io.IOException;

import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.api.widgets.IMapWidget;

/**
 * Custom MapMenuFactory that attaches or enables a CCTV/Video button
 * in the radial menu of sensor markers on the map.
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
        // In ATAK, standard sensor markers have CoT types starting with "b-m-p-s"
        // (e.g., b-m-p-s-p-loc), or have metadata tags for sensor/camera.
        String type = item.getType();
        boolean isSensor = (type != null && (type.startsWith("b-m-p-s")
                || type.toLowerCase().contains("sensor")
                || type.toLowerCase().contains("camera")))
                || item.hasMetaValue("sensor")
                || item.hasMetaValue("camera")
                || item.hasMetaValue("videoUID");

        if (!isSensor) {
            // Not a sensor marker, let other factories or ATAK default handle it
            return null;
        }

        // Generate the base radial menu widget for this item
        final MapMenuWidget menuWidget = defaultResourceFactory.create(item);
        if (menuWidget == null) {
            return null;
        }

        // 1. Look for existing video button in the sensor's radial menu (e.g. from menus/b-m-p-s-p-loc.xml)
        MapMenuButtonWidget videoButton = findVideoButton(menuWidget);

        if (videoButton != null) {
            // Enable the built-in video button on the sensor wheel and wire our action
            videoButton.setDisabled(false);
            videoButton.setOnButtonClickHandler(new IMapMenuButtonWidget.OnButtonClickHandler() {
                @Override
                public boolean isSupported(Object o) {
                    return true;
                }

                @Override
                public void performAction(Object o) {
                    onSensorRadialButtonClicked(item);
                }
            });
        } else {
            // 2. If the marker's menu does not have a video button, inject our custom button
            addCustomRadialButton(menuWidget, item);
        }

        return menuWidget;
    }

    private MapMenuButtonWidget findVideoButton(MapMenuWidget menuWidget) {
        for (MapWidget child : menuWidget.getChildWidgets()) {
            if (child instanceof MapMenuButtonWidget) {
                MapMenuButtonWidget buttonWidget = (MapMenuButtonWidget) child;
                WidgetIcon icon = buttonWidget.getIcon();
                if (icon != null) {
                    MapDataRef iconRef = icon.getIconRef(0);
                    if (iconRef != null && iconRef.toUri() != null) {
                        String uri = iconRef.toUri();
                        if (uri.contains("video.png") || uri.contains("camera.png")) {
                            return buttonWidget;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void addCustomRadialButton(MapMenuWidget menuWidget, final MapItem item) {
        MapView mapView = MapView.getMapView();
        MapMenuButtonWidget buttonWidget = new MapMenuButtonWidget(mapView.getContext());

        WidgetIcon widgetIcon = new WidgetIcon.Builder()
                .setImageRef(0, MapDataRef.parseUri("asset://icons/camera.png"))
                .setAnchor(16, 16)
                .setSize(32, 32)
                .build();
        buttonWidget.setIcon(widgetIcon);

        buttonWidget.setOrientation(buttonWidget.getOrientationAngle(), menuWidget.getInnerRadius());
        if (menuWidget.getChildWidgetCount() > 0) {
            float buttonWeight = 0f;
            for (IMapWidget child : menuWidget.getChildren()) {
                if (child instanceof MapMenuButtonWidget) {
                    buttonWeight += ((MapMenuButtonWidget) child).getLayoutWeight();
                }
            }
            buttonWeight /= menuWidget.getChildWidgetCount();
            buttonWidget.setLayoutWeight(buttonWeight);
        }

        buttonWidget.setOnButtonClickHandler(new IMapMenuButtonWidget.OnButtonClickHandler() {
            @Override
            public boolean isSupported(Object o) {
                return true;
            }

            @Override
            public void performAction(Object o) {
                onSensorRadialButtonClicked(item);
            }
        });

        menuWidget.addWidget(buttonWidget);
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
