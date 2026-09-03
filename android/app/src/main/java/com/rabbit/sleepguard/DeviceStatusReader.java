package com.rabbit.sleepguard;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.PowerManager;

import org.json.JSONObject;

public final class DeviceStatusReader {
    public static final class Snapshot {
        public final int batteryLevel;
        public final boolean charging;
        public final Float batteryTemperatureC;
        public final String networkType;
        public final boolean networkConnected;
        public final boolean screenOn;

        Snapshot(int batteryLevel, boolean charging, Float batteryTemperatureC,
                 String networkType, boolean networkConnected, boolean screenOn) {
            this.batteryLevel = batteryLevel;
            this.charging = charging;
            this.batteryTemperatureC = batteryTemperatureC;
            this.networkType = networkType;
            this.networkConnected = networkConnected;
            this.screenOn = screenOn;
        }

        public JSONObject toJson() {
            JSONObject body = new JSONObject();
            try {
                body.put("battery_level", batteryLevel);
                body.put("charging", charging);
                body.put("battery_temperature_c", batteryTemperatureC == null ? JSONObject.NULL : batteryTemperatureC);
                body.put("network_type", networkType);
                body.put("network_connected", networkConnected);
                body.put("screen_on", screenOn);
            } catch (Exception ignored) {
            }
            return body;
        }
    }

    private DeviceStatusReader() {
    }

    public static Snapshot read(Context context) {
        int batteryLevel = 0;
        boolean charging = false;
        Float temperature = null;
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) batteryLevel = Math.max(0, Math.min(100, Math.round(level * 100f / scale)));
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            int tenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (tenths != Integer.MIN_VALUE) temperature = tenths / 10f;
        }

        String networkType = "offline";
        boolean connected = false;
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        if (connectivity != null) {
            Network network = connectivity.getActiveNetwork();
            NetworkCapabilities capabilities = network == null ? null : connectivity.getNetworkCapabilities(network);
            if (capabilities != null) {
                connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) networkType = "vpn";
                else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) networkType = "wifi";
                else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) networkType = "cellular";
                else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) networkType = "ethernet";
                else networkType = connected ? "other" : "offline";
            }
        }

        PowerManager power = context.getSystemService(PowerManager.class);
        boolean screenOn = power != null && power.isInteractive();
        return new Snapshot(batteryLevel, charging, temperature, networkType, connected, screenOn);
    }
}
