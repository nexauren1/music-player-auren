package com.auren.musicplayer;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BluetoothActivity extends ComponentActivity {
    private static final int REQUEST_BT = 71;
    private BluetoothAdapter adapter;
    private LinearLayout deviceList;
    private TextView status;
    private boolean receiverRegistered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) addDevice(device);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                status.setText("Pesquisa concluída. Escolha um dispositivo.");
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (adapter != null && adapter.isEnabled()) scan();
            }
        }
    };

    private final Set<String> seen = new HashSet<>();
    private final List<BluetoothDevice> devices = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = BluetoothAdapter.getDefaultAdapter();
        buildUi();
        requestBluetoothPermissions();
    }

    @Override protected void onDestroy() {
        stopScan();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = column();
        root.setBackgroundColor(ThemeManager.surface(this));

        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(12), dp(6));
        bar.setBackgroundColor(ThemeManager.card(this));

        TextView back = text("‹", 34, R.color.text_primary);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));

        LinearLayout titles = column();
        TextView title = text("Bluetooth", 20, R.color.text_primary);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        titles.addView(title);
        titles.addView(text("Gerencie a ligação Bluetooth pelo Android", 11, R.color.text_secondary), margins(0,2,0,0));
        bar.addView(titles, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(18), dp(18), dp(18), dp(28));

        LinearLayout hero = rounded(ThemeManager.resolve(this, R.color.accent_soft), 22);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView heroTitle = text("Conectar dispositivo", 18, R.color.text_primary);
        heroTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        hero.addView(heroTitle);
        hero.addView(text("O Nexauren procura dispositivos próximos. Escolha um dispositivo emparelhado ou encontrado e abra as definições do Android para concluir a ligação.", 12, R.color.text_secondary),
                margins(0,4,0,10));
        Button scanButton = button("Pesquisar dispositivos");
        scanButton.setOnClickListener(v -> scan());
        hero.addView(scanButton);
        content.addView(hero);

        status = text("Pronto para pesquisar.", 12, R.color.text_secondary);
        content.addView(status, margins(2, 12, 2, 8));

        TextView paired = text("DISPOSITIVOS DISPONÍVEIS", 10, R.color.auren_primary);
        paired.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        content.addView(paired, margins(2, 4, 0, 8));

        deviceList = column();
        content.addView(deviceList);

        TextView note = text("A ligação final de áudio é controlada pelo Android e pelo próprio dispositivo Bluetooth.", 11, R.color.text_secondary);
        content.addView(note, margins(2, 14, 2, 0));

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        if (adapter == null) {
            status.setText("Este dispositivo não oferece Bluetooth.");
        } else if (!adapter.isEnabled()) {
            status.setText("Bluetooth está desligado. Ative-o para pesquisar.");
        }
    }

    private void requestBluetoothPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!permissions.isEmpty()) ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), REQUEST_BT);
        else scan();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQUEST_BT) scan();
    }

    private void scan() {
        if (adapter == null) return;
        if (!hasBluetoothPermission()) {
            requestBluetoothPermissions();
            return;
        }
        if (!adapter.isEnabled()) {
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            status.setText("Ative o Bluetooth e volte à pesquisa.");
            return;
        }

        stopScan();
        seen.clear();
        devices.clear();
        deviceList.removeAllViews();

        try {
            for (BluetoothDevice device : adapter.getBondedDevices()) addDevice(device);
        } catch (SecurityException ignored) {}

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;

        try {
            status.setText("A pesquisar dispositivos próximos…");
            adapter.startDiscovery();
        } catch (SecurityException e) {
            status.setText("Não foi possível iniciar a pesquisa Bluetooth.");
        }
    }

    private void stopScan() {
        if (adapter != null && hasBluetoothPermission()) {
            try { if (adapter.isDiscovering()) adapter.cancelDiscovery(); } catch (SecurityException ignored) {}
        }
        if (receiverRegistered) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void addDevice(BluetoothDevice device) {
        try {
            String address = device.getAddress();
            if (address == null || !seen.add(address)) return;
            devices.add(device);
            Collections.sort(devices, Comparator.comparing(this::safeName, String.CASE_INSENSITIVE_ORDER));
            renderDevices();
        } catch (SecurityException ignored) {}
    }

    private void renderDevices() {
        deviceList.removeAllViews();
        if (devices.isEmpty()) {
            deviceList.addView(emptyCard("Nenhum dispositivo encontrado ainda."));
            return;
        }
        for (BluetoothDevice device : devices) {
            String name = safeName(device);
            String state;
            try {
                state = device.getBondState() == BluetoothDevice.BOND_BONDED ? "Emparelhado" : "Encontrado agora";
            } catch (SecurityException e) {
                state = "Encontrado";
            }
            LinearLayout card = rounded(ThemeManager.card(this), 18);
            card.setPadding(dp(14), dp(10), dp(10), dp(10));
            LinearLayout info = column();
            TextView title = text(name, 15, R.color.text_primary);
            title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            info.addView(title);
            info.addView(text(state, 11, R.color.text_secondary), margins(0,2,0,0));
            card.addView(info, new LinearLayout.LayoutParams(0, dp(56), 1));
            Button connect = button("Abrir definições");
            connect.setOnClickListener(v -> confirmDevice(device));
            card.addView(connect, new LinearLayout.LayoutParams(dp(104), dp(50)));
            deviceList.addView(card, margins(0,0,0,8));
        }
    }

    private void confirmDevice(BluetoothDevice device) {
        String name = safeName(device);
        new AlertDialog.Builder(this)
                .setTitle("Confirmar dispositivo")
                .setMessage("Ligar ao dispositivo \"" + name + "\"?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar", (d,w) -> {
                    status.setText("Abrindo ligação para " + name + "…");
                    Intent settings = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    startActivity(settings);
                })
                .show();
    }

    private String safeName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? "Dispositivo Bluetooth" : name.trim();
        } catch (SecurityException e) {
            return "Dispositivo Bluetooth";
        }
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        b.setTextColor(ThemeManager.textOnAccent(this));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeManager.accent(this)));
        return b;
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
    }

    private LinearLayout rounded(int color, int radius) {
        LinearLayout v = column();
        v.setBackground(roundDrawable(color, radius));
        return v;
    }

    private android.graphics.drawable.GradientDrawable roundDrawable(int color, int radius) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private LinearLayout.LayoutParams margins(int l,int t,int r,int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(dp(l),dp(t),dp(r),dp(b));
        return p;
    }

    private TextView text(String s,float size,int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(ThemeManager.resolve(this,color));
        return v;
    }

    private View emptyCard(String s) {
        LinearLayout c = rounded(ThemeManager.card(this),18);
        c.setPadding(dp(15),dp(15),dp(15),dp(15));
        c.addView(text(s,12,R.color.text_secondary));
        return c;
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
}
