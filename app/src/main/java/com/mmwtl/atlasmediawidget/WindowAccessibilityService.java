package com.mmwtl.atlasmediawidget;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Collects interactive windows and their screen bounds for the overlay visibility policy. */
public final class WindowAccessibilityService extends AccessibilityService {
    private static final long LAUNCHER_APP_LIST_REFRESH_MS = 250L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService windowExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "atlas-accessibility-windows");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "atlas-accessibility-metadata");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private final AtomicBoolean metadataRefreshQueued = new AtomicBoolean();
    private final AtomicLong eventGeneration = new AtomicLong();
    private final Set<Integer> pendingMetadataWindowIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<Integer, WindowMetadata> metadataCache = new ConcurrentHashMap<>();
    private volatile String lastEventPackage = "";
    private volatile String lastEventClass = "";
    private volatile int lastEventWindowId = -1;
    private volatile boolean eventContextFresh;
    private volatile boolean metadataRefreshRequested;
    private volatile boolean launcherMetadataRefreshRequested;
    private volatile boolean destroyed;
    private final Runnable launcherAppListRefresh = () -> requestRefresh(true, true);

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_SCROLLED;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.notificationTimeout = 50L;
            setServiceInfo(info);
        }
        requestRefresh();
        AppLog.info("Window accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        boolean relevant = event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED;
        if (relevant) {
            String eventPackage = text(event.getPackageName());
            String eventClass = text(event.getClassName());
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && HeadUnitWindowRules.forceHide(eventPackage, eventClass)) {
                // These firmware-specific activities are known to represent a fullscreen shell
                // transition. Hide immediately; the regular window snapshot still runs to
                // reconcile the complete state and to decide when HOME can be shown again.
                OverlayService.onAccessibilityForceHide(eventPackage, eventClass);
            }
            boolean passiveOwnWindow = getPackageName().equals(eventPackage)
                    && (eventClass.isEmpty() || eventClass.startsWith("android."));
            if (!eventPackage.isEmpty() && !passiveOwnWindow) {
                lastEventPackage = eventPackage;
                lastEventClass = eventClass;
                lastEventWindowId = event.getWindowId();
                eventContextFresh = true;
            } else if (eventPackage.isEmpty() || passiveOwnWindow) {
                // A package-less or passive overlay event does not identify the foreground
                // Activity. Do not label the new active window with a stale component, and do
                // not let our own non-focusable card become the foreground app.
                eventContextFresh = false;
                lastEventWindowId = -1;
            }
            // The framework query is Binder-backed and can be slow on the head unit. Keep it
            // off the process main thread; the resulting immutable snapshot still triggers an
            // immediate overlay recheck.
            requestRefresh(
                    true,
                    LauncherAllAppsViewDetector.isLauncherPackage(eventPackage)
            );
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        metadataRefreshRequested = false;
        launcherMetadataRefreshRequested = false;
        mainHandler.removeCallbacks(launcherAppListRefresh);
        windowExecutor.shutdownNow();
        metadataExecutor.shutdownNow();
        pendingMetadataWindowIds.clear();
        metadataCache.clear();
        AccessibilityWindowState.markUnavailable();
        notifyOverlayService();
        AppLog.info("Window accessibility service disconnected");
        super.onDestroy();
    }

    private void refreshWindows(boolean scheduleMetadata, boolean forceLauncherMetadata) {
        if (destroyed) {
            return;
        }
        List<WindowObservation> observations = new ArrayList<>();
        List<Integer> windowsWithoutMetadata = new ArrayList<>();
        Set<Integer> liveWindowIds = new HashSet<>();
        boolean eventWindowPresent = false;
        boolean useEventContext = scheduleMetadata && eventContextFresh;
        String eventPackage = useEventContext ? lastEventPackage : "";
        String eventClass = useEventContext ? lastEventClass : "";
        int eventWindowId = useEventContext ? lastEventWindowId : -1;
        try {
            Trace.beginSection("AtlasAccessibility.getWindows");
            List<AccessibilityWindowInfo> windows;
            try {
                windows = getWindows();
            } finally {
                Trace.endSection();
            }
            if (windows != null) {
                boolean eventWindowAssigned = false;
                for (AccessibilityWindowInfo window : windows) {
                    if (window == null) {
                        continue;
                    }
                    int windowId = window.getId();
                    liveWindowIds.add(windowId);
                    Rect bounds = new Rect();
                    window.getBoundsInScreen(bounds);
                    boolean active = window.isActive();
                    boolean focused = window.isFocused();
                    WindowMetadata metadata = metadataCache.get(windowId);
                    if ((active || focused)
                            && !eventWindowAssigned
                            && !eventPackage.isEmpty()) {
                        // The event identifies the window that changed and is available without
                        // waiting for a potentially slow root query. Prefer it for the active
                        // window, even when the root cache belongs to the previous Activity.
                        if (windowId == eventWindowId) {
                            boolean appListVisible = metadata != null
                                    && metadata.launcherAppListVisible;
                            metadata = new WindowMetadata(
                                    eventPackage,
                                    eventClass,
                                    appListVisible
                            );
                            metadataCache.put(windowId, metadata);
                            eventWindowAssigned = true;
                            eventWindowPresent = true;
                        }
                    }
                    if (metadata == null) {
                        metadata = WindowMetadata.empty();
                    }
                    boolean launcherWindow = LauncherAllAppsViewDetector.isLauncherPackage(
                            metadata.packageName
                    ) || (windowId == eventWindowId
                            && LauncherAllAppsViewDetector.isLauncherPackage(eventPackage));
                    if (scheduleMetadata && (needsMetadata(metadata)
                            || (forceLauncherMetadata && launcherWindow
                            && (active || focused || windowId == eventWindowId)))) {
                        windowsWithoutMetadata.add(windowId);
                    }
                    observations.add(new WindowObservation(
                            metadata.packageName,
                            metadata.className,
                            window.getType(),
                            active,
                            focused,
                            window.getLayer(),
                            bounds.left,
                            bounds.top,
                            bounds.right,
                            bounds.bottom,
                            metadata.launcherAppListVisible
                    ));
                }
                // Window IDs are not a lifecycle-managed cache key. Remove closed windows so a
                // long-running accessibility service cannot retain metadata forever, and so a
                // recycled ID cannot inherit a stale package/class pair.
                for (Integer cachedId : metadataCache.keySet()) {
                    if (!liveWindowIds.contains(cachedId)) metadataCache.remove(cachedId);
                }
                for (Integer pendingId : pendingMetadataWindowIds) {
                    if (!liveWindowIds.contains(pendingId)) pendingMetadataWindowIds.remove(pendingId);
                }
            }
            WindowManager manager = getSystemService(WindowManager.class);
            if (manager == null) {
                throw new IllegalStateException("WindowManager is unavailable");
            }
            WindowMetrics metrics = manager.getCurrentWindowMetrics();
            Rect display = metrics.getBounds();
            if (!destroyed) {
                AccessibilityWindowState.update(
                        observations,
                        display.width(),
                        display.height(),
                        eventWindowPresent ? eventPackage : "",
                        eventWindowPresent ? eventClass : ""
                );
                scheduleLauncherAppListRefresh(hasVisibleLauncherAppList(observations));
            }
        } catch (RuntimeException error) {
            AccessibilityWindowState.markUnavailable();
            AppLog.warnRateLimited(
                    "accessibility-windows",
                    "Cannot collect interactive accessibility windows",
                    error
            );
        }
        if (scheduleMetadata && !windowsWithoutMetadata.isEmpty()) {
            requestMetadataRefresh(windowsWithoutMetadata);
        }
        if (!destroyed) {
            notifyOverlayService();
        }
    }

    /**
     * Enriches only the metadata cache. This is deliberately isolated from the fast bounds
     * collector because getRoot() can block for seconds on the target head unit. A blocked root
     * query must not prevent a newer fullscreen/window-state event from reaching the policy.
     */
    private void enrichMetadata() {
        if (destroyed) {
            return;
        }
        Set<Integer> requestedIds = new HashSet<>();
        for (Integer id : pendingMetadataWindowIds) {
            if (id != null && pendingMetadataWindowIds.remove(id)) {
                requestedIds.add(id);
            }
        }
        if (requestedIds.isEmpty() || destroyed) {
            return;
        }

        boolean metadataChanged = false;
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    if (window == null || !requestedIds.contains(window.getId())) {
                        continue;
                    }
                    WindowMetadata metadata = readRootMetadata(window);
                    if (metadata != null) {
                        metadataCache.put(window.getId(), metadata);
                        metadataChanged = true;
                    }
                }
            }
        } catch (RuntimeException error) {
            AppLog.warnRateLimited(
                    "accessibility-window-metadata",
                    "Cannot enrich accessibility window metadata",
                    error
            );
        }

        if (metadataChanged && !destroyed) {
            // Publish the new package/component values without scheduling another metadata pass.
            requestRefresh(false);
        }
    }

    private WindowMetadata readRootMetadata(AccessibilityWindowInfo window) {
        AccessibilityNodeInfo root = null;
        try {
            root = window.getRoot();
            if (root == null) {
                return null;
            }
            String packageName = text(root.getPackageName());
            String className = text(root.getClassName());
            if (packageName.isEmpty() && className.isEmpty()) {
                return null;
            }
            boolean appListVisible = LauncherAllAppsViewDetector.isLauncherPackage(packageName)
                    && containsAllAppsMarker(root);
            return new WindowMetadata(packageName, className, appListVisible);
        } catch (RuntimeException error) {
            AppLog.warnRateLimited(
                    "accessibility-window-root",
                    "Cannot inspect accessibility window root",
                    error
            );
            return null;
        } finally {
            if (root != null) {
                recycle(root);
            }
        }
    }

    private void requestMetadataRefresh(List<Integer> windowIds) {
        if (destroyed) {
            return;
        }
        pendingMetadataWindowIds.addAll(windowIds);
        if (!metadataRefreshQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            metadataExecutor.execute(() -> {
                try {
                    enrichMetadata();
                } finally {
                    metadataRefreshQueued.set(false);
                    if (!destroyed && !pendingMetadataWindowIds.isEmpty()) {
                        requestMetadataRefresh(new ArrayList<>(pendingMetadataWindowIds));
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {
            metadataRefreshQueued.set(false);
        }
    }

    private void requestRefresh() {
        requestRefresh(true, false);
    }

    private void requestRefresh(boolean scheduleMetadata) {
        requestRefresh(scheduleMetadata, false);
    }

    private void requestRefresh(boolean scheduleMetadata, boolean forceLauncherMetadata) {
        if (scheduleMetadata) {
            metadataRefreshRequested = true;
        }
        if (forceLauncherMetadata) {
            launcherMetadataRefreshRequested = true;
        }
        eventGeneration.incrementAndGet();
        if (destroyed || !refreshQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            windowExecutor.execute(() -> {
                long consumedGeneration = -1L;
                try {
                    while (!destroyed) {
                        long generation = eventGeneration.get();
                        boolean scheduleMetadataForSnapshot = metadataRefreshRequested;
                        metadataRefreshRequested = false;
                        boolean forceLauncherScan = launcherMetadataRefreshRequested;
                        launcherMetadataRefreshRequested = false;
                        refreshWindows(scheduleMetadataForSnapshot, forceLauncherScan);
                        consumedGeneration = eventGeneration.get();
                        if (generation == eventGeneration.get()) {
                            break;
                        }
                    }
                } finally {
                    refreshQueued.set(false);
                    if (!destroyed && eventGeneration.get() != consumedGeneration) {
                        // A state change may have arrived while the previous snapshot was being
                        // collected. Requeue only when the worker did not consume the latest one.
                        requestRefresh();
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {
            refreshQueued.set(false);
        }
    }

    private void notifyOverlayService() {
        OverlayService.onAccessibilityWindowsChanged();
    }

    private void scheduleLauncherAppListRefresh(boolean appListVisible) {
        mainHandler.removeCallbacks(launcherAppListRefresh);
        if (appListVisible && !destroyed) {
            mainHandler.postDelayed(launcherAppListRefresh, LAUNCHER_APP_LIST_REFRESH_MS);
        }
    }

    private static boolean hasVisibleLauncherAppList(List<WindowObservation> observations) {
        for (WindowObservation observation : observations) {
            if (observation != null && observation.launcherAppListVisible) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static void recycle(AccessibilityNodeInfo node) {
        node.recycle();
    }

    @SuppressWarnings("deprecation")
    private static boolean containsAllAppsMarker(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        try {
            if (!node.isVisibleToUser()) {
                return false;
            }
            if (LauncherAllAppsViewDetector.isAllAppsMarker(
                    text(node.getViewIdResourceName()),
                    text(node.getClassName()),
                    text(node.getText()))) {
                return true;
            }
            int childCount = node.getChildCount();
            for (int index = 0; index < childCount; index++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(index);
                    if (containsAllAppsMarker(child)) {
                        return true;
                    }
                } catch (RuntimeException error) {
                    AppLog.warnRateLimited(
                            "accessibility-app-list",
                            "Cannot inspect launcher app-list node",
                            error
                    );
                } finally {
                    if (child != null) {
                        recycle(child);
                    }
                }
            }
        } catch (RuntimeException error) {
            AppLog.warnRateLimited(
                    "accessibility-app-list",
                    "Cannot inspect launcher app-list root",
                    error
            );
        }
        return false;
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static boolean needsMetadata(WindowMetadata metadata) {
        return metadata == null
                || metadata.packageName.isEmpty()
                || metadata.className.isEmpty();
    }

    private static final class WindowMetadata {
        final String packageName;
        final String className;
        final boolean launcherAppListVisible;

        WindowMetadata(String packageName, String className, boolean launcherAppListVisible) {
            this.packageName = text(packageName);
            this.className = text(className);
            this.launcherAppListVisible = launcherAppListVisible;
        }

        static WindowMetadata empty() {
            return new WindowMetadata("", "", false);
        }
    }
}
