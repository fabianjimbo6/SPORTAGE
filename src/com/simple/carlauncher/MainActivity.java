package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * Kia Sportage dashboard launcher (1024x600): speedometer, GPS stats, the Sportage with
 * headlights, navigation and music cards and a bottom dock.
 */
public class MainActivity extends Activity implements NowPlaying.Listener, Gps.Listener {

    private static final String MODEL_NAME = "SPORTAGE";
    private static final String MODEL_YEAR = "2018";
    private static final int RED = 0xFFD6283A;
    private static final int RED_LINE = 0xCCC4121F;
    private static final int PANEL = 0xF2101014;
    private static final int GRAY = 0xFFA8A8B2;

    private final Handler handler = new Handler();
    private NowPlaying nowPlaying;
    private Gps gps;
    private boolean askedLocation;

    private GaugeView gauge;
    private TextView tripValue, maxValue, satValue, altValue, headingValue;
    private ImageView car, carLights;
    private TextView lightsChip;
    private boolean manualLights;
    private TextView songTitle, songArtist;
    private ImageView playBtn;
    private String createdProfile;

    private String profileStamp() {
        return Profile.name(this) + "|" + Profile.carPhotoFile(this).lastModified();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), 0, dp(10), dp(6));
        root.setBackground(background());

        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout body = new LinearLayout(this);
        body.addView(buildLeft(), weight(0.62f, 0));
        body.addView(buildCenter(), weight(1.2f, dp(8)));
        body.addView(buildRight(), weight(0.78f, dp(8)));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(62));
        dlp.topMargin = dp(8);
        root.addView(buildDock(), dlp);
        setContentView(root);

        nowPlaying = NowPlaying.get(this);
        gps = Gps.get(this);
        createdProfile = profileStamp();
        startService(new Intent(this, GreeterService.class));
    }

    private android.graphics.drawable.Drawable background() {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0B0507, 0xFF060608, 0xFF020203});
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(dp(520));
        glow.setGradientCenter(0.5f, 0.0f);
        glow.setColors(new int[]{0x33D6283A, 0x00D6283A});
        return new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{base, glow});
    }

    // ---------- Header ----------

    private View buildHeader() {
        FrameLayout bar = new FrameLayout(this);

        View line = new View(this);
        line.setBackgroundColor(RED);
        bar.addView(line, new FrameLayout.LayoutParams(-1, dp(2), Gravity.TOP));

        LinearLayout left = new LinearLayout(this);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.addView(headerIcon(R.drawable.ic_w_sun, 0xFFFFFFFF,
                () -> startActivity(new Intent(this, ScreenOffActivity.class))));
        left.addView(headerIcon(R.drawable.ic_phone, 0xFFFFFFFF, this::openBluetooth));
        bar.addView(left, new FrameLayout.LayoutParams(-2, -1, Gravity.START));

        TextView title = new TextView(this);
        SpannableStringBuilder sb = new SpannableStringBuilder("KIA  " + MODEL_NAME + " ");
        int start = sb.length();
        sb.append(MODEL_YEAR);
        sb.setSpan(new ForegroundColorSpan(RED), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(sb);
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setLetterSpacing(0.08f);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        title.setGravity(Gravity.CENTER);
        Ui.pressable(title, 0.96f);
        title.setOnLongClickListener(v -> {
            startActivity(new Intent(this, SetupActivity.class));
            return true;
        });
        bar.addView(title, new FrameLayout.LayoutParams(-2, -1, Gravity.CENTER));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        ImageView bt = new ImageView(this);
        bt.setImageResource(R.drawable.ic_bluetooth);
        bt.setColorFilter(0xFF4A90FF);
        right.addView(bt, new LinearLayout.LayoutParams(dp(20), dp(20)));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        tcol.setGravity(Gravity.END);
        tcol.setPadding(dp(8), 0, 0, 0);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm a");
        time.setTextSize(19);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        time.setIncludeFontPadding(false);
        tcol.addView(time);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEE d MMM");
        date.setFormat12Hour("EEE d MMM");
        date.setTextSize(10);
        date.setTextColor(RED);
        date.setIncludeFontPadding(false);
        date.setGravity(Gravity.END);
        tcol.addView(date);
        right.addView(tcol);
        Ui.pressable(right, 0.96f);
        right.setOnClickListener(v -> openSettings());
        bar.addView(right, new FrameLayout.LayoutParams(-2, -1, Gravity.END));
        return bar;
    }

    private View headerIcon(int icon, int tint, Runnable action) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(tint);
        iv.setPadding(dp(8), dp(8), dp(8), dp(8));
        Ui.pressable(iv, 0.85f);
        iv.setOnClickListener(v -> action.run());
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return iv;
    }

    // ---------- Left column: speedometer, trip, top speed ----------

    private View buildLeft() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        gauge = new GaugeView(this);
        col.addView(gauge, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        View trip = statCard("VIAJE", "0.0 km", 20, true);
        tripValue = (TextView) ((LinearLayout) trip).getChildAt(1);
        Ui.pressable(trip, 0.97f);
        trip.setOnLongClickListener(v -> {
            gps.resetTrip();
            updateGps();
            return true;
        });
        View max = statCard("VEL. MÁX", "0 km/h", 20, true);
        maxValue = (TextView) ((LinearLayout) max).getChildAt(1);
        row.addView(trip, weight(1f, 0));
        row.addView(max, weight(1f, dp(6)));
        col.addView(row, new LinearLayout.LayoutParams(-1, dp(62)));
        return col;
    }

    private View statCard(String label, String value, float valueSp, boolean left) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(left ? Gravity.CENTER_VERTICAL | Gravity.START : Gravity.CENTER);
        c.setPadding(dp(10), dp(4), dp(6), dp(4));
        c.setBackground(new Chamfer(dp(9), PANEL, RED_LINE, dp(1)));
        TextView l = Ui.text(this, label, 9, GRAY);
        l.setLetterSpacing(0.1f);
        l.setGravity(left ? Gravity.START : Gravity.CENTER);
        c.addView(l);
        TextView v = Ui.text(this, value, valueSp, Color.WHITE);
        v.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        v.setGravity(left ? Gravity.START : Gravity.CENTER);
        c.addView(v);
        return c;
    }

    // ---------- Center column: GPS stats + the car ----------

    private View buildCenter() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout stats = new LinearLayout(this);
        View sat = statCard("SATÉLITES", "-- / --", 18, true);
        satValue = (TextView) ((LinearLayout) sat).getChildAt(1);
        View alt = statCard("ALTITUD", "-- m", 18, true);
        altValue = (TextView) ((LinearLayout) alt).getChildAt(1);
        View hd = statCard("RUMBO", "--", 18, true);
        headingValue = (TextView) ((LinearLayout) hd).getChildAt(1);
        stats.addView(sat, weight(1f, 0));
        stats.addView(alt, weight(1f, dp(6)));
        stats.addView(hd, weight(1f, dp(6)));
        col.addView(stats, new LinearLayout.LayoutParams(-1, dp(52)));

        FrameLayout box = new FrameLayout(this);
        car = new ImageView(this);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights = new ImageView(this);
        carLights.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights.setAlpha(0f);
        android.graphics.Bitmap own = Profile.carPhoto(this);
        int bundled = Profile.bundledCar(this, false), bundledLights = Profile.bundledCar(this, true);
        if (own != null) {
            car.setImageBitmap(own);
        } else if (bundled != 0) {
            car.setImageResource(bundled);
            if (bundledLights != 0) carLights.setImageResource(bundledLights);
        } else {
            car.setImageResource(R.drawable.ic_car);
            car.setColorFilter(0x40FFFFFF);
        }
        box.addView(car, new FrameLayout.LayoutParams(-1, -1));
        box.addView(carLights, new FrameLayout.LayoutParams(-1, -1));

        TextView badge = new TextView(this);
        SpannableStringBuilder bs = new SpannableStringBuilder(Profile.name(this).toUpperCase() + " ");
        int s0 = bs.length();
        bs.append("MODDING");
        bs.setSpan(new ForegroundColorSpan(RED), s0, bs.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        badge.setText(bs);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(18);
        badge.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        badge.setShadowLayer(dp(6), 0, 0, 0xFF000000);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        blp.setMargins(0, dp(8), dp(6), 0);
        box.addView(badge, blp);

        lightsChip = Ui.text(this, "LUCES: OFF", 10, Color.WHITE);
        lightsChip.setPadding(dp(10), dp(5), dp(10), dp(5));
        lightsChip.setBackground(new Chamfer(dp(6), 0xCC15151A, 0x55FFFFFF, dp(1)));
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        llp.setMargins(dp(4), 0, 0, dp(6));
        box.addView(lightsChip, llp);

        box.setOnClickListener(v -> {
            manualLights = !manualLights;
            showCarLights(carLightsOn() || manualLights);
        });
        box.setOnLongClickListener(v -> {
            startActivity(new Intent(this, SetupActivity.class));
            return true;
        });
        col.addView(box, new LinearLayout.LayoutParams(-1, 0, 1f));
        return col;
    }

    // ---------- Right column: navigation + music ----------

    private View buildRight() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        FrameLayout nav = new FrameLayout(this);
        nav.setBackground(new Chamfer(dp(12), 0xFF0B0F18, RED_LINE, dp(1)));
        nav.setPadding(dp(2), dp(2), dp(2), dp(2));
        nav.addView(new NavPreview(this), new FrameLayout.LayoutParams(-1, -1));
        TextView search = Ui.text(this, "Buscar destino", 12, 0xFFE8E8EE);
        search.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        search.setPadding(dp(12), 0, dp(10), 0);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xCC2A2D38);
        pill.setCornerRadius(dp(16));
        search.setBackground(pill);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, dp(32), Gravity.TOP);
        slp.setMargins(dp(10), dp(10), dp(10), 0);
        nav.addView(search, slp);
        TextView navLabel = Ui.text(this, "NAVEGACIÓN", 12, Color.WHITE);
        navLabel.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        navLabel.setLetterSpacing(0.05f);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        nlp.setMargins(dp(12), 0, 0, dp(8));
        nav.addView(navLabel, nlp);
        Ui.pressable(nav, 0.98f);
        nav.setOnClickListener(v -> openMaps());
        col.addView(nav, new LinearLayout.LayoutParams(-1, 0, 1.15f));

        LinearLayout music = new LinearLayout(this);
        music.setOrientation(LinearLayout.VERTICAL);
        music.setPadding(dp(12), dp(8), dp(12), dp(8));
        music.setBackground(new Chamfer(dp(12), PANEL, RED_LINE, dp(1)));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView note = new ImageView(this);
        note.setImageResource(R.drawable.ic_music);
        note.setColorFilter(0xFF4A90FF);
        top.addView(note, new LinearLayout.LayoutParams(dp(18), dp(18)));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        tcol.setPadding(dp(8), 0, 0, 0);
        songTitle = Ui.text(this, "Música", 13, Color.WHITE);
        songTitle.setGravity(Gravity.START);
        songTitle.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        songArtist = Ui.text(this, "Reproductor", 10, GRAY);
        songArtist.setGravity(Gravity.START);
        tcol.addView(songTitle);
        tcol.addView(songArtist);
        top.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1f));
        music.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(plain(R.drawable.ic_prev, () -> nowPlaying.skip(false)),
                new LinearLayout.LayoutParams(dp(46), dp(46)));
        playBtn = plain(R.drawable.ic_play, () -> nowPlaying.playPause());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(52), dp(52));
        plp.setMargins(dp(14), 0, dp(14), 0);
        controls.addView(playBtn, plp);
        controls.addView(plain(R.drawable.ic_next, () -> nowPlaying.skip(true)),
                new LinearLayout.LayoutParams(dp(46), dp(46)));
        music.addView(controls, new LinearLayout.LayoutParams(-1, 0, 1f));

        Ui.pressable(music, 0.98f);
        music.setOnClickListener(v -> nowPlaying.open(this));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, 0, 0.85f);
        mlp.topMargin = dp(8);
        col.addView(music, mlp);
        return col;
    }

    private ImageView plain(int icon, Runnable action) {
        ImageView b = new ImageView(this);
        b.setImageResource(icon);
        b.setColorFilter(Color.WHITE);
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        Ui.pressable(b, 0.85f);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    // ---------- Dock ----------

    private View buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.addView(dockItem(R.drawable.ic_home, "INICIO", true, null), weight(1f, 0));
        dock.addView(dockItem(R.drawable.ic_nav, "MAPS", false, this::openMaps), weight(1f, dp(5)));
        dock.addView(dockItem(R.drawable.ic_spotify, "SPOTIFY", false,
                () -> openPackage("com.spotify.music", "Spotify")), weight(1f, dp(5)));
        dock.addView(dockItem(R.drawable.ic_whatsapp, "WHATSAPP", false,
                () -> openPackage("com.whatsapp", "WhatsApp")), weight(1f, dp(5)));

        TextView logo = new TextView(this);
        logo.setText("KIA");
        logo.setTextColor(RED);
        logo.setTextSize(26);
        logo.setLetterSpacing(0.18f);
        logo.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        logo.setGravity(Gravity.CENTER);
        logo.setShadowLayer(dp(8), 0, 0, 0xCCD6283A);
        Ui.pressable(logo, 0.92f);
        logo.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        dock.addView(logo, weight(1.05f, dp(5)));

        dock.addView(dockItem(R.drawable.ic_music, "MÚSICA", false, () -> nowPlaying.open(this)),
                weight(1f, dp(5)));
        dock.addView(dockItem(R.drawable.ic_bluetooth, "BLUETOOTH", false, this::openBluetooth),
                weight(1f, dp(5)));
        dock.addView(dockItem(R.drawable.ic_car, "AUTO", false,
                () -> startActivity(new Intent(this, OilActivity.class))), weight(1f, dp(5)));
        dock.addView(dockItem(R.drawable.ic_apps, "APPS", false,
                () -> startActivity(new Intent(this, AppsActivity.class))), weight(1f, dp(5)));
        return dock;
    }

    private View dockItem(int icon, String label, boolean selected, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                selected ? new int[]{0xFFE5384C, 0xFFA81C2B} : new int[]{0xFF15151B, 0xFF0E0E12});
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), selected ? 0xFFFF6B7A : 0x33FFFFFF);
        item.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Color.WHITE);
        item.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView t = Ui.text(this, label, 9, Color.WHITE);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        t.setPadding(0, dp(2), 0, 0);
        item.addView(t);
        if (action != null) {
            Ui.pressable(item, 0.93f);
            item.setOnClickListener(v -> action.run());
        }
        return item;
    }

    // ---------- Live data ----------

    @Override
    public void onGps() {
        updateGps();
    }

    private void updateGps() {
        gps.tick();
        gauge.setSpeed(gps.kmh);
        tripValue.setText(String.format(java.util.Locale.US, "%.1f km", gps.tripKm));
        maxValue.setText(Math.round(gps.maxKmh) + " km/h");
        satValue.setText(gps.total > 0 ? gps.used + " / " + gps.total : "-- / --");
        altValue.setText(Double.isNaN(gps.altitude) || !gps.fix ? "-- m" : Math.round(gps.altitude) + " m");
        headingValue.setText(gps.bearing < 0 ? "--" : Math.round(gps.bearing) + "° " + compass(gps.bearing));
    }

    private static String compass(float b) {
        String[] n = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
        return n[Math.round(b / 45f) % 8];
    }

    @Override
    public void onNowPlayingChanged() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();
        songTitle.setText(empty ? "Música" : t.title);
        songArtist.setText(empty ? "Reproductor" : (t.artist.isEmpty() ? nowPlaying.label(s) : t.artist));
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    // ---------- Car lights (night mode / ILL wire / tap) ----------

    private final android.content.BroadcastReceiver illReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            showCarLights(i.getByteExtra("extra_ill_state", (byte) 0) != 0 || manualLights);
        }
    };

    private boolean carLightsOn() {
        int state = android.provider.Settings.System.getInt(getContentResolver(), "mcu_no_source_device_state", 0);
        return (state & 2) != 0 || Ui.isDark(this);
    }

    private void showCarLights(boolean on) {
        lightsChip.setText(on ? "LUCES: ON" : "LUCES: OFF");
        lightsChip.setTextColor(on ? 0xFFFFE08A : Color.WHITE);
        carLights.animate().alpha(on ? 1f : 0f).setDuration(600).start();
        car.animate().alpha(on ? 0f : 1f).setDuration(600).start();
    }

    private final Runnable periodic = new Runnable() {
        public void run() {
            updateGps();
            showCarLights(carLightsOn() || manualLights);
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- Shortcuts ----------

    private void openMaps() {
        PackageManager pm = getPackageManager();
        String[] pkgs = {"com.google.android.apps.maps", "com.waze", "com.google.android.apps.mapslite"};
        for (String pkg : pkgs) {
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i != null && safeStart(i)) return;
        }
        if (!launchByKeyword("maps", "waze", "naveg", "gps", "navi")) {
            safeStart(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=")));
        }
    }

    private void openPackage(String pkg, String name) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null || !safeStart(i)) toast(name + " no está instalado");
    }

    private void openBluetooth() {
        PackageManager pm = getPackageManager();
        String[] btPkgs = {
            "com.autochips.bluetooth", "com.xyauto.bluetooth", "com.xyauto.bt",
            "com.android.bluetooth", "com.mediatek.bluetooth"
        };
        for (String pkg : btPkgs) {
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i != null && safeStart(i)) return;
        }
        if (!launchByKeyword("bluetooth", "bt music", "btmusic", "teléfono", "telefono", "phone")) {
            safeStart(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
        }
    }

    private void openSettings() {
        safeStart(new Intent(android.provider.Settings.ACTION_SETTINGS));
    }

    private boolean launchByKeyword(String... keywords) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            String label = (ri.loadLabel(pm) + " " + ri.activityInfo.packageName).toLowerCase();
            for (String k : keywords) {
                if (label.contains(k)) {
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                    return safeStart(i);
                }
            }
        }
        return false;
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ---------- Lifecycle ----------

    @Override
    protected void onResume() {
        super.onResume();
        if (!profileStamp().equals(createdProfile)) {
            recreate();
            return;
        }
        if (!Gps.hasPermission(this) && !askedLocation) {
            askedLocation = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 7);
        }
        registerReceiver(illReceiver, new android.content.IntentFilter("com.nwd.ACTION_ILL_STATE_CHANGE"));
        gps.start(this);
        nowPlaying.addListener(this);
        onNowPlayingChanged();
        boolean lights = carLightsOn() || manualLights;
        carLights.setAlpha(lights ? 1f : 0f);
        car.setAlpha(lights ? 0f : 1f);
        handler.post(periodic);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (Gps.hasPermission(this)) gps.start(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(illReceiver);
        } catch (RuntimeException ignored) { }
        handler.removeCallbacks(periodic);
        gps.stop();
        nowPlaying.removeListener(this);
    }

    private LinearLayout.LayoutParams weight(float w, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, w);
        lp.leftMargin = left;
        return lp;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    static void pressable(View v) {
        Ui.pressable(v, 0.94f);
    }

    @Override
    public void onBackPressed() {
        // Home screen: nothing to go back to.
    }
}
