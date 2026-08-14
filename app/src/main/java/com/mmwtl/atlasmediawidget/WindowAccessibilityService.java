package com.mmwtl.atlasmediawidget;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

/** Collects interactive windows and their screen bounds for the overlay visibility policy. */
public final class WindowAccessibilityService extends AccessibilityService {
    private String lastEventPackage = "";
    private String lastEventClass = "";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            info.notificationTimeout = 50L;
            setServiceInfo(info);
        }
        refreshWindows();
        AppLog.info("Window accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastEventPackage = text(event.getPackageName());
            lastEventClass = text(event.getClassName());
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // The overlay service is notified on every relevant accessibility event. Its
            // executor keeps the framework query off the main/UI thread.
            refreshWindows();
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        AccessibilityWindowState.markUnavailable();
        notifyOverlayService();
        AppLog.info("Window accessibility service disconnected");
        super.onDestroy();
    }

    private void refreshWindows() {
        List<WindowObservation> observations = new ArrayList<>();
        boolean eventWindowPresent = false;
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    if (window == null) {
                        continue;
                    }
                    Rect bounds = new Rect();
                    window.getBoundsInScreen(bounds);
                    boolean active = window.isActive();
                    boolean focused = window.isFocused();
                    String packageName = "";
                    String className = "";
                    try {
                        AccessibilityNodeInfo root = window.getRoot();
                        if (root != null) {
                            packageName = text(root.getPackageName());
                            className = text(root.getClassName());
                        }
                    } catch (RuntimeException error) {
                        AppLog.warnRateLimited(
                                "accessibility-window-root",
                                "Cannot inspect accessibility window root",
                                error
                        );
                    }
                    if (packageName.equals(lastEventPackage)
                            && (active || focused)
                            && !lastEventClass.isEmpty()) {
                        // The event class is the actual activity component; the root may only
                        // expose a generic view class on this firmware.
                        className = lastEventClass;
                        eventWindowPresent = true;
                    }
                    observations.add(new WindowObservation(
                            packageName,
                            className,
                            window.getType(),
                            active,
                            focused,
                            window.getLayer(),
                            bounds.left,
                            bounds.top,
                            bounds.right,
                            bounds.bottom
                    ));
                }
            }
            WindowManager manager = getSystemService(WindowManager.class);
            if (manager == null) {
                throw new IllegalStateException("WindowManager is unavailable");
            }
            WindowMetrics metrics = manager.getCurrentWindowMetrics();
            Rect display = metrics.getBounds();
            AccessibilityWindowState.update(
                    observations,
                    display.width(),
                    display.height(),
                    eventWindowPresent ? lastEventPackage : "",
                    eventWindowPresent ? lastEventClass : ""
            );
        } catch (RuntimeException error) {
            AccessibilityWindowState.markUnavailable();
            AppLog.warnRateLimited(
                    "accessibility-windows",
                    "Cannot collect interactive accessibility windows",
                    error
            );
        }
        notifyOverlayService();
    }

    private void notifyOverlayService() {
        OverlayService.onAccessibilityWindowsChanged();
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
