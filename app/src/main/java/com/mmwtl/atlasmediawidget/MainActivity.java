package com.mmwtl.atlasmediawidget;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private Prefs prefs;
    private TextView permissionStatus;
    private TextView bridgeStatus;
    private Button serviceButton;
    private Switch autoStart;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        setContentView(buildContent());
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 28), Ui.dp(this, 38), Ui.dp(this, 28), Ui.dp(this, 38));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("Atlas Media Widget", 30, Ui.PRIMARY, Typeface.BOLD);
        root.addView(title);
        TextView intro = text(
                "Медиакарточка поверх главного экрана. Состояние и команды приходят из "
                        + "GInputBridge mediaapi v1.",
                17, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams introParams = fullWrap();
        introParams.topMargin = Ui.dp(this, 10);
        root.addView(intro, introParams);

        LinearLayout accessCard = card();
        LinearLayout.LayoutParams cardParams = fullWrap();
        cardParams.topMargin = Ui.dp(this, 28);
        root.addView(accessCard, cardParams);
        accessCard.addView(text("Разрешения", 20, Ui.PRIMARY, Typeface.BOLD));
        permissionStatus = text("", 15, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams statusParams = fullWrap();
        statusParams.topMargin = Ui.dp(this, 10);
        accessCard.addView(permissionStatus, statusParams);
        Button overlay = actionButton("Разрешить поверх окон");
        overlay.setOnClickListener(v -> openOverlaySettings());
        accessCard.addView(overlay, buttonParams());
        Button usage = actionButton("Разрешить историю использования");
        usage.setOnClickListener(v -> openUsageSettings());
        accessCard.addView(usage, buttonParams());

        LinearLayout bridgeCard = card();
        LinearLayout.LayoutParams bridgeParams = fullWrap();
        bridgeParams.topMargin = Ui.dp(this, 18);
        root.addView(bridgeCard, bridgeParams);
        bridgeCard.addView(text("GInputBridge", 20, Ui.PRIMARY, Typeface.BOLD));
        bridgeStatus = text("", 15, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams bridgeStatusParams = fullWrap();
        bridgeStatusParams.topMargin = Ui.dp(this, 10);
        bridgeCard.addView(bridgeStatus, bridgeStatusParams);
        Button openBridge = actionButton("Открыть GInputBridge");
        openBridge.setOnClickListener(v -> openGInputBridge());
        bridgeCard.addView(openBridge, buttonParams());

        LinearLayout serviceCard = card();
        LinearLayout.LayoutParams serviceParams = fullWrap();
        serviceParams.topMargin = Ui.dp(this, 18);
        root.addView(serviceCard, serviceParams);
        serviceCard.addView(text("Виджет", 20, Ui.PRIMARY, Typeface.BOLD));
        serviceButton = actionButton("Запустить");
        serviceButton.setOnClickListener(v -> toggleService());
        serviceCard.addView(serviceButton, buttonParams());
        autoStart = new Switch(this);
        autoStart.setText("Автозапуск после загрузки ГУ");
        autoStart.setTextColor(Ui.PRIMARY);
        autoStart.setTextSize(16);
        autoStart.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        autoStart.setOnCheckedChangeListener((button, checked) -> {
            if (button.isPressed()) prefs.putBoolean(Prefs.KEY_AUTO_START, checked);
        });
        LinearLayout.LayoutParams switchParams = fullWrap();
        switchParams.topMargin = Ui.dp(this, 14);
        serviceCard.addView(autoStart, switchParams);

        TextView note = text(
                "Карточка отображается только когда HOME находится на переднем плане. "
                        + "Перетаскивание выполняется за верхнюю строку MEDIA.",
                14, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams noteParams = fullWrap();
        noteParams.topMargin = Ui.dp(this, 24);
        root.addView(note, noteParams);
        return scroll;
    }

    private void refresh() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean usage = ForegroundAppDetector.hasUsageAccess(this);
        permissionStatus.setText("Поверх окон: " + yesNo(overlay)
                + "\nИстория использования: " + yesNo(usage));
        permissionStatus.setTextColor(overlay && usage ? Ui.ACCENT : Ui.ERROR);
        boolean bridgeInstalled = isPackageInstalled(MediaBridgeContract.SERVICE_PACKAGE);
        bridgeStatus.setText(bridgeInstalled
                ? "Пакет установлен. Требуется ветка mediaapi с protocol v1."
                : "Пакет com.salat.gbinder не найден.");
        bridgeStatus.setTextColor(bridgeInstalled ? Ui.ACCENT : Ui.ERROR);
        boolean enabled = prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        serviceButton.setText(enabled ? "Остановить" : "Запустить");
        serviceButton.setEnabled(enabled || overlay && usage);
        autoStart.setChecked(prefs.getBoolean(Prefs.KEY_AUTO_START, false));
        requestNotificationPermissionIfNeeded();
    }

    private void toggleService() {
        boolean enabled = prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        if (enabled) {
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            OverlayService.stop(this);
        } else if (Settings.canDrawOverlays(this) && ForegroundAppDetector.hasUsageAccess(this)) {
            try {
                prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
                OverlayService.start(this);
            } catch (RuntimeException error) {
                prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
                AppLog.warn("Cannot start overlay service", error);
                Toast.makeText(this, "Не удалось запустить виджет", Toast.LENGTH_LONG).show();
            }
        }
        refresh();
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void openUsageSettings() {
        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    private void openGInputBridge() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(MediaBridgeContract.SERVICE_PACKAGE);
        if (launch == null) {
            Toast.makeText(this, "GInputBridge не установлен", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(launch);
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18));
        card.setBackground(Ui.background(Ui.CARD, 20, this));
        return card;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(Ui.PRIMARY);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.NESTED));
        return button;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(0, 1.12f);
        return view;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = fullWrap();
        params.topMargin = Ui.dp(this, 12);
        return params;
    }

    private static String yesNo(boolean value) {
        return value ? "разрешено" : "не разрешено";
    }
}
