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
import gov.tak.api.widgets.IWidgetBackground;

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

        // Default dimensions matching ATAK's sensor radial menu specification
        float radius = menuWidget.getInnerRadius() > 0 ? menuWidget.getInnerRadius() : 65f;
        float width = menuWidget.getButtonWidth() > 0 ? menuWidget.getButtonWidth() : 90f;
        float span = menuWidget.getButtonSpan() > 0 ? menuWidget.getButtonSpan() : 45f;
        float weight = span;
        int iconWidth = 32;
        int iconHeight = 32;
        int anchorX = 16;
        int anchorY = 16;
        IWidgetBackground bg = null;

        MapMenuButtonWidget sampleSibling = null;
        int siblingCount = 0;
        float totalWeight = 0f;

        // Inspect existing buttons in the radial menu to precisely match radius, width, weight, and background
        for (IMapWidget child : menuWidget.getChildren()) {
            if (child instanceof MapMenuButtonWidget) {
                MapMenuButtonWidget sibling = (MapMenuButtonWidget) child;
                if (sampleSibling == null) {
                    sampleSibling = sibling;
                }
                totalWeight += sibling.getLayoutWeight();
                siblingCount++;
            }
        }

        if (sampleSibling != null) {
            if (sampleSibling.getOrientationRadius() > 0) {
                radius = sampleSibling.getOrientationRadius();
            }
            if (sampleSibling.getButtonWidth() > 0) {
                width = sampleSibling.getButtonWidth();
            }
            if (sampleSibling.getButtonSpan() > 0) {
                span = sampleSibling.getButtonSpan();
            }
            if (siblingCount > 0 && totalWeight > 0) {
                weight = totalWeight / siblingCount;
            } else if (sampleSibling.getLayoutWeight() > 0) {
                weight = sampleSibling.getLayoutWeight();
            }
            bg = sampleSibling.getWidgetBackground();

            if (sampleSibling.getIcon() != null) {
                WidgetIcon siblingIcon = sampleSibling.getIcon();
                if (siblingIcon.getIconWidth() > 0 && siblingIcon.getIconHeight() > 0) {
                    iconWidth = siblingIcon.getIconWidth();
                    iconHeight = siblingIcon.getIconHeight();
                    anchorX = siblingIcon.getAnchorX();
                    anchorY = siblingIcon.getAnchorY();
                }
            }
        }

        // Use ATAK's built-in camera icon to give our CCTV button a clear, distinct visual
        WidgetIcon widgetIcon = new WidgetIcon.Builder()
                .setImageRef(0, MapDataRef.parseUri("asset://icons/camera.png"))
                .setAnchor(anchorX, anchorY)
                .setSize(iconWidth, iconHeight)
                .build();
        newButton.setIcon(widgetIcon);

        // Inherit styling and geometry so the button's radius, width, and weight seamlessly match the circle
        if (bg != null) {
            newButton.setWidgetBackground(bg);
        }
        newButton.setOrientation(0f, radius);
        newButton.setButtonSize(span, width);
        newButton.setLayoutWeight(weight);

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

                // Present all available MediaMTX streams for selection
                com.atakmap.android.plugintemplate.plugin.mediamtx.StreamSelectDialog.show(
                        MapView.getMapView().getContext(), item, plugin);
            }
        });
    }
}
