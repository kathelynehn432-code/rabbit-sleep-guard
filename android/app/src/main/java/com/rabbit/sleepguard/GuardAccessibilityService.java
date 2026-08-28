package com.rabbit.sleepguard;

import android.accessibilityservice.AccessibilityService;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Set;

public final class GuardAccessibilityService extends AccessibilityService {
    private static final long POLL_INTERVAL_MS = 300_000L;
    private static final long EVENT_DEBOUNCE_MS = 2_500L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private GuardPreferences preferences;
    private GuardApiClient api;
    private WindowManager windowManager;
    private View overlay;
    private String lastPackage = "";
    private long lastHandledAt = 0L;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (api != null && preferences.configured()) {
                api.status(result -> main.post(() -> {
                    if (result.requestOk && result.active) {
                        GuardNotification.showActive(GuardAccessibilityService.this, result.attempts);
                    } else if (result.requestOk) {
                        GuardNotification.hideActive(GuardAccessibilityService.this);
                    }
                }));
            }
            main.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        preferences = new GuardPreferences(this);
        api = new GuardApiClient(preferences);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        GuardNotification.createChannels(this);
        main.removeCallbacks(poll);
        main.post(poll);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null || preferences == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;

        String packageName = event.getPackageName().toString();
        if (packageName.equals(getPackageName()) || packageName.startsWith("com.android.systemui")) return;
        Set<String> blocked = preferences.blockedPackages();
        if (!blocked.contains(packageName)) return;

        long now = System.currentTimeMillis();
        if (packageName.equals(lastPackage) && now - lastHandledAt < EVENT_DEBOUNCE_MS) return;
        lastPackage = packageName;
        lastHandledAt = now;

        String appName = appLabel(packageName);
        api.blocked(appName, packageName, result -> main.post(() -> {
            if (result.requestOk && result.active && !result.ignored) {
                blockNow(appName, result.attempts);
                GuardNotification.showActive(this, result.attempts);
                GuardNotification.caught(this, appName, result.attempts);
            } else if (!result.requestOk && preferences.cachedActive()) {
                int attempts = Math.max(1, preferences.attempts() + 1);
                blockNow(appName, attempts);
                GuardNotification.showActive(this, attempts);
            }
        }));
    }

    private String appLabel(String packageName) {
        try {
            PackageManager manager = getPackageManager();
            ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            return manager.getApplicationLabel(info).toString();
        } catch (Exception ignored) {
            return packageName;
        }
    }

    private void blockNow(String appName, int attempts) {
        showOverlay(appName, attempts);
        main.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 180L);
        if (preferences.lockScreen()) {
            main.postDelayed(this::lockScreenIfAllowed, 550L);
        } else {
            main.postDelayed(this::removeOverlay, 1_100L);
        }
    }

    private void showOverlay(String appName, int attempts) {
        removeOverlay();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(30), dp(30), dp(30), dp(30));
        card.setBackgroundColor(Color.rgb(14, 19, 32));

        TextView title = new TextView(this);
        title.setText("被比比抓到了");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView body = new TextView(this);
        body.setText("“" + appName + "”先放下。\n今晚第 " + attempts + " 次，兔酱该乖乖睡觉了。");
        body.setTextColor(Color.rgb(202, 210, 235));
        body.setTextSize(18f);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(18), 0, 0);
        card.addView(body);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(card, params);
            overlay = card;
            main.postDelayed(this::removeOverlay, 1_500L);
        } catch (Exception ignored) {
            overlay = null;
        }
    }

    private void lockScreenIfAllowed() {
        DevicePolicyManager policy = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, GuardDeviceAdminReceiver.class);
        if (policy != null && policy.isAdminActive(admin)) {
            try {
                policy.lockNow();
            } catch (SecurityException ignored) {
                removeOverlay();
            }
        }
        removeOverlay();
    }

    private void removeOverlay() {
        if (overlay == null || windowManager == null) return;
        try {
            windowManager.removeView(overlay);
        } catch (Exception ignored) {
        }
        overlay = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        removeOverlay();
        super.onDestroy();
    }
}
