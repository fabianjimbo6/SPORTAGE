package com.simple.carlauncher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks what is playing from any source and routes controls back to it:
 * apps with a media session (Spotify, YouTube Music…), the factory USB/SD player
 * (com.android.music.* broadcasts) and Bluetooth audio (com.bt.* broadcasts).
 */
final class NowPlaying {

    enum Source { APP, LOCAL, BT }

    interface Listener {
        void onNowPlayingChanged();
    }

    private static final String LOCAL_PLAYER = "com.nwd.android.music.ui/.home_horizontalActivity";

    /** State reported by one source. */
    static final class Track {
        String title = "", artist = "";
        Bitmap art;
        boolean playing;
        long updated;
    }

    private static NowPlaying instance;

    static NowPlaying get(Context c) {
        if (instance == null) instance = new NowPlaying(c.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final List<Listener> listeners = new ArrayList<>();
    private final Track app = new Track(), local = new Track(), bt = new Track();
    final BtClient btClient = new BtClient();

    private final MediaSessionManager sessions;
    private final ComponentName notificationListener;
    private MediaController controller;

    private NowPlaying(Context c) {
        ctx = c;
        sessions = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
        notificationListener = new ComponentName(c, MediaListener.class);

        IntentFilter f = new IntentFilter();
        f.addAction("com.android.music.metachanged");
        f.addAction("com.android.music.playstatechanged");
        f.addAction("com.bt.ACTION_AVRCP_MUSIC_ID3");
        f.addAction("com.bt.ACTION_BT_MUSIC_PLAY");
        f.addAction("com.bt.ACTION_BT_MUSIC_PAUSE");
        f.addAction("com.bt.ACTION_A2DP_RELEASE");
        f.addAction("com.bt.ACTION_AVRCP_RELEASE");
        c.registerReceiver(receiver, f);

        try {
            sessions.addOnActiveSessionsChangedListener(list -> refreshSessions(), notificationListener);
        } catch (SecurityException ignored) {
            // Notification access not granted: app sessions are not visible.
        }
        refreshSessions();

        btClient.bind(c, () -> {
            bt.playing = btClient.isMusicPlaying();
            if (bt.playing) bt.updated = SystemClock.elapsedRealtime();
            btClient.queryId3();
            notifyListeners();
        });
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null) return;
            i.setExtrasClassLoader(NowPlaying.class.getClassLoader());
            try {
                handle(a, i);
            } catch (RuntimeException e) {
                // Factory apps may attach extras we cannot unparcel; never crash the launcher for it.
                android.util.Log.w("NowPlaying", "Ignoring broadcast " + a, e);
            }
        }

        private void handle(String a, Intent i) {
            long now = SystemClock.elapsedRealtime();
            switch (a) {
                case "com.android.music.metachanged":
                case "com.android.music.playstatechanged":
                    if (i.hasExtra("track")) local.title = nz(i.getStringExtra("track"));
                    if (i.hasExtra("artist")) local.artist = nz(i.getStringExtra("artist"));
                    local.playing = i.getBooleanExtra("playing", local.playing);
                    local.updated = now;
                    break;
                case "com.bt.ACTION_AVRCP_MUSIC_ID3":
                    bt.title = nz(i.getStringExtra("extra_avrcp_id3_title"));
                    bt.artist = nz(i.getStringExtra("extra_avrcp_id3_artist"));
                    if (!bt.title.isEmpty()) bt.updated = now;
                    break;
                case "com.bt.ACTION_BT_MUSIC_PLAY":
                    bt.playing = true;
                    bt.updated = now;
                    btClient.queryId3();
                    break;
                case "com.bt.ACTION_BT_MUSIC_PAUSE":
                    bt.playing = false;
                    break;
                default: // A2DP/AVRCP released: phone disconnected
                    bt.playing = false;
                    bt.title = "";
                    bt.artist = "";
                    bt.updated = 0;
                    break;
            }
            notifyListeners();
        }
    };

    // ---------- App media sessions ----------

    private final MediaController.Callback sessionCallback = new MediaController.Callback() {
        public void onMetadataChanged(MediaMetadata m) { readSession(); }
        public void onPlaybackStateChanged(PlaybackState s) { readSession(); }
        public void onSessionDestroyed() { refreshSessions(); }
    };

    private void refreshSessions() {
        List<MediaController> list = null;
        try {
            list = sessions.getActiveSessions(notificationListener);
        } catch (SecurityException ignored) { }
        MediaController pick = null;
        if (list != null) {
            for (MediaController c : list) {
                if ("com.android.server.telecom".equals(c.getPackageName())) continue;
                if (pick == null) pick = c;
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { pick = c; break; }
            }
        }
        if (controller != null) controller.unregisterCallback(sessionCallback);
        controller = pick;
        if (controller != null) controller.registerCallback(sessionCallback);
        readSession();
    }

    private void readSession() {
        if (controller == null) {
            app.title = "";
            app.artist = "";
            app.art = null;
            app.playing = false;
            app.updated = 0;
        } else {
            MediaMetadata m = controller.getMetadata();
            PlaybackState s = controller.getPlaybackState();
            app.title = m == null ? "" : nz(m.getString(MediaMetadata.METADATA_KEY_TITLE));
            app.artist = m == null ? "" : nz(m.getString(MediaMetadata.METADATA_KEY_ARTIST));
            Bitmap art = m == null ? null : m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null && m != null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            app.art = art;
            boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
            if (playing || !app.title.isEmpty()) app.updated = SystemClock.elapsedRealtime();
            app.playing = playing;
        }
        notifyListeners();
    }

    // ---------- Public API ----------

    /** The source to show: whatever is playing (most recent first), else the last one used. */
    Source current() {
        Source best = null;
        long bestTime = -1;
        for (Source s : Source.values()) {
            Track t = track(s);
            if (t.playing && t.updated > bestTime) { best = s; bestTime = t.updated; }
        }
        if (best != null) return best;
        for (Source s : Source.values()) {
            Track t = track(s);
            if (t.updated > 0 && t.updated > bestTime) { best = s; bestTime = t.updated; }
        }
        return best;
    }

    Track track(Source s) {
        return s == Source.APP ? app : s == Source.LOCAL ? local : bt;
    }

    String label(Source s) {
        if (s == Source.BT) return "Bluetooth";
        if (s == Source.LOCAL) return "USB";
        if (controller != null) {
            try {
                PackageManager pm = ctx.getPackageManager();
                return pm.getApplicationLabel(pm.getApplicationInfo(controller.getPackageName(), 0)).toString();
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return "Música";
    }

    void playPause() {
        Source s = current();
        if (s == Source.BT && Profile.isNwd(ctx) && btClient.isBound()) {
            if (Ui.radioIsSource(ctx)) {
                // Radio has the speakers (the phone may even be playing muted): bring Bluetooth in.
                Ui.userChoseRadioAt = 0;
                Ui.selectBluetooth(ctx);
                btClient.playControl(BtClient.PLAY);
            } else {
                btClient.playControl(bt.playing ? BtClient.PAUSE : BtClient.PLAY);
            }
        } else if (s == Source.APP && controller != null) {
            if (app.playing) {
                controller.getTransportControls().pause();
            } else {
                controller.getTransportControls().play();
                // Respaldo: algunas apps ignoran play() pero responden a la tecla multimedia PLAY.
                new android.os.Handler().postDelayed(() -> {
                    if (!app.playing) mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
                }, 700);
            }
        } else if (s == null || (s != Source.LOCAL && controller == null)) {
            // Nada reproduciendo / sin sesion conocida: la tecla multimedia no llega a ninguna app.
            playFromScratch();
        } else {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }
    }

    /** True when the launcher is allowed to read media sessions (Notification access). */
    private boolean hasNotificationAccess() {
        String enabled = android.provider.Settings.Secure.getString(
                ctx.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(ctx.getPackageName());
    }

    /**
     * Nothing is playing and no player is registered: ask for notification access (once) so
     * Spotify/YouTube Music can be controlled, send PLAY, and if nothing starts open the player.
     */
    private void playFromScratch() {
        if (!hasNotificationAccess() && !askedAccess) {
            askedAccess = true;
            android.widget.Toast.makeText(ctx,
                    "Activa \"Inicio\" en Acceso a notificaciones para controlar la música",
                    android.widget.Toast.LENGTH_LONG).show();
            try {
                ctx.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (RuntimeException ignored) { }
        }
        mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
        new android.os.Handler().postDelayed(() -> {
            refreshSessions();
            if (!app.playing && !local.playing && !bt.playing) open(ctx);
        }, 1200);
    }

    private boolean askedAccess;

    void skip(boolean next) {
        Source s = current();
        if (s == Source.BT) {
            btClient.playControl(next ? BtClient.NEXT : BtClient.PREVIOUS);
        } else if (s == Source.APP && controller != null) {
            if (next) controller.getTransportControls().skipToNext();
            else controller.getTransportControls().skipToPrevious();
        } else {
            mediaKey(next ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
    }

    /** Opens the screen that belongs to the current source (supports XYAuto F9211B & NWD). */
    void open(Context c) {
        Source s = current();
        if (s == Source.BT && Profile.isNwd(c)) {
            c.startActivity(new Intent(c, BtActivity.class));
            return;
        }
        if (s == Source.APP && controller != null) {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(controller.getPackageName());
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return;
            }
        }
        if (Profile.isNwd(c)) {
            Ui.launch(c, LOCAL_PLAYER);
            return;
        }
        // Fallback for XYAuto F9211B / standard Android radios:
        PackageManager pm = c.getPackageManager();
        String[] musicPkgs = {
            "com.Spotify.music", "com.spotify.music", "com.spotify.lite",
            "com.google.android.apps.youtube.music", "com.autochips.music",
            "com.xyauto.music", "com.android.music", "com.autochips.bluetooth"
        };
        for (String pkg : musicPkgs) {
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(launch);
                return;
            }
        }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String label = (ri.loadLabel(pm) + " " + ri.activityInfo.packageName).toLowerCase();
            if (label.contains("music") || label.contains("música") || label.contains("spotify") || label.contains("audio")) {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return;
            }
        }
    }

    void addListener(Listener l) {
        listeners.add(l);
        refreshSessions();
        if (btClient.isBound()) btClient.queryId3();
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyListeners() {
        for (Listener l : new ArrayList<>(listeners)) l.onNowPlayingChanged();
    }

    private void mediaKey(int keyCode) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        long now = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
