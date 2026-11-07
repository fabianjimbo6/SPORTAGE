package com.simple.carlauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Looks up the album cover on the internet (iTunes Search) when the phone does not
 * send one over Bluetooth. Needs the radio to be connected to Wi-Fi; if not, nothing happens.
 */
final class CoverArt {
    interface Callback {
        void onCover(Bitmap cover);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Bitmap> CACHE = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();
    private static String loading = "";

    private CoverArt() {
    }

    static void load(final String artist, final String title, final Callback cb) {
        if (title == null || title.trim().isEmpty()) return;
        final String a = artist == null ? "" : artist;
        final String key = (a + "|" + title).toLowerCase();
        Bitmap hit;
        synchronized (CACHE) {
            hit = CACHE.get(key);
            if (hit == null && MISSING.contains(key)) return;
        }
        if (hit != null) {
            cb.onCover(hit);
            return;
        }
        if (key.equals(loading)) return;
        loading = key;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = fetch(a, title);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = "";
                        synchronized (CACHE) {
                            if (b != null) CACHE.put(key, b);
                            else MISSING.add(key);
                        }
                        if (b != null) cb.onCover(b);
                    }
                });
            }
        }).start();
    }

    private static String clean(String s) {
        return s.replaceAll("\\s*[\\(\\[].*?[\\)\\]]", "").trim();
    }

    private static Bitmap fetch(String artist, String title) {
        try {
            String term = (clean(artist) + " " + clean(title)).trim();
            String url = "https://itunes.apple.com/search?media=music&entity=song&limit=1&term="
                    + URLEncoder.encode(term, "UTF-8");
            String json = new String(download(url), "UTF-8");
            JSONArray results = new JSONObject(json).getJSONArray("results");
            if (results.length() == 0) return null;
            String art = results.getJSONObject(0).optString("artworkUrl100", "");
            if (art.isEmpty()) return null;
            byte[] img = download(art.replace("100x100", "600x600"));
            return BitmapFactory.decodeByteArray(img, 0, img.length);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] download(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        try {
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
