package com.simple.carlauncher;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;

/** GPS state for the dashboard: speed, satellites, altitude, heading, trip distance and top speed. */
final class Gps implements LocationListener {

    interface Listener {
        void onGps();
    }

    private static Gps instance;

    static Gps get(Context c) {
        if (instance == null) instance = new Gps(c.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final LocationManager lm;
    private final SharedPreferences prefs;
    private Listener listener;
    private boolean running;
    private Location last;

    float kmh;
    boolean fix;
    int used, total;
    double altitude = Double.NaN;
    float bearing = -1;
    float tripKm;
    float maxKmh;
    long lastFixAt;

    private final GnssStatus.Callback statusCb = new GnssStatus.Callback() {
        @Override
        public void onSatelliteStatusChanged(GnssStatus s) {
            int n = s.getSatelliteCount(), u = 0;
            for (int i = 0; i < n; i++) if (s.usedInFix(i)) u++;
            total = n;
            used = u;
            if (listener != null) listener.onGps();
        }
    };

    private Gps(Context c) {
        ctx = c;
        lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        prefs = c.getSharedPreferences("trip", Context.MODE_PRIVATE);
        tripKm = prefs.getFloat("trip_km", 0f);
        maxKmh = prefs.getFloat("max_kmh", 0f);
    }

    static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    void start(Listener l) {
        listener = l;
        if (running || !hasPermission(ctx)) return;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            lm.registerGnssStatusCallback(statusCb);
            running = true;
        } catch (RuntimeException ignored) {
            // No GPS or no permission: the dashboard simply shows dashes.
        }
    }

    void stop() {
        listener = null;
        if (!running) return;
        running = false;
        try {
            lm.removeUpdates(this);
            lm.unregisterGnssStatusCallback(statusCb);
        } catch (RuntimeException ignored) { }
        prefs.edit().putFloat("trip_km", tripKm).putFloat("max_kmh", maxKmh).apply();
    }

    void resetTrip() {
        tripKm = 0;
        maxKmh = 0;
        prefs.edit().putFloat("trip_km", 0f).putFloat("max_kmh", 0f).apply();
    }

    /** Called every second by the UI: no recent fix means the car is not reporting speed. */
    void tick() {
        if (fix && SystemClock.elapsedRealtime() - lastFixAt > 5000) {
            fix = false;
            kmh = 0;
            bearing = -1;
        }
    }

    @Override
    public void onLocationChanged(Location l) {
        lastFixAt = SystemClock.elapsedRealtime();
        fix = true;
        float v = l.hasSpeed() ? l.getSpeed() * 3.6f : 0f;
        kmh = v < 2f ? 0f : v;
        altitude = l.hasAltitude() ? l.getAltitude() : Double.NaN;
        bearing = (l.hasBearing() && kmh > 3f) ? l.getBearing() : -1;
        if (kmh > maxKmh && kmh < 260f) maxKmh = kmh;
        if (last != null && l.hasAccuracy() && l.getAccuracy() < 30f && kmh > 3f) {
            float dist = last.distanceTo(l);
            if (dist < 200f) tripKm += dist / 1000f;
        }
        last = l;
        if (listener != null) listener.onGps();
    }

    @Override
    public void onStatusChanged(String p, int s, Bundle b) { }

    @Override
    public void onProviderEnabled(String p) { }

    @Override
    public void onProviderDisabled(String p) { }
}
