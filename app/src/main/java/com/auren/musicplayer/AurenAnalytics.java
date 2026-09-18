package com.auren.musicplayer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Calendar;

/**
 * Local-only listening intelligence for Nexauren Music Player.
 * Stores aggregate listening data on-device. No network is required.
 */
public final class AurenAnalytics {
    private static final String PREFS = "nexauren_analytics_v1";
    private static final long SESSION_GAP_MS = 20 * 60 * 1000L;
    private static final long TICK_CAP_MS = 5000L;

    private AurenAnalytics() {}

    public static synchronized void recordPlayStart(Context context, long trackId, String artist) {
        if (trackId <= 0) return;
        SharedPreferences p = prefs(context);
        Calendar now = Calendar.getInstance();
        String day = dayKey(now);
        String week = weekKey(now);
        String month = monthKey(now);
        String hour = String.valueOf(now.get(Calendar.HOUR_OF_DAY));

        Set<String> artists = new HashSet<>(p.getStringSet("unique_artists", new HashSet<>()));
        if (artist != null && !artist.trim().isEmpty()) artists.add(artist.trim());

        Calendar hourNow = Calendar.getInstance();
        int hourNowValue = hourNow.get(Calendar.HOUR_OF_DAY);
        int night = p.getInt("night_plays", 0);
        if (hourNowValue >= 20 || hourNowValue < 5) night++;

        p.edit()
                .putInt("plays_total", p.getInt("plays_total", 0) + 1)
                .putInt("plays_track_" + trackId, p.getInt("plays_track_" + trackId, 0) + 1)
                .putStringSet("unique_artists", artists)
                .putInt("night_plays", night)
                .putInt("plays_day_" + day, p.getInt("plays_day_" + day, 0) + 1)
                .putInt("plays_week_" + week, p.getInt("plays_week_" + week, 0) + 1)
                .putInt("plays_month_" + month, p.getInt("plays_month_" + month, 0) + 1)
                .putInt("plays_hour_" + hour + "_" + trackId,
                        p.getInt("plays_hour_" + hour + "_" + trackId, 0) + 1)
                .putString("artist_" + trackId, safe(artist))
                .putLong("last_play_track", trackId)
                .putLong("last_play_at", System.currentTimeMillis())
                .apply();
        ensureStreak(p, now);
    }

    public static synchronized void tick(Context context, long trackId, String artist, long listenedMs, boolean playing) {
        if (!playing || trackId <= 0) return;
        long safeMs = Math.max(0L, Math.min(TICK_CAP_MS, listenedMs));
        if (safeMs == 0L) return;

        SharedPreferences p = prefs(context);
        Calendar now = Calendar.getInstance();
        String day = dayKey(now);
        String week = weekKey(now);
        String month = monthKey(now);

        long total = p.getLong("time_total_ms", 0L) + safeMs;
        long dayMs = p.getLong("time_day_" + day, 0L) + safeMs;
        long weekMs = p.getLong("time_week_" + week, 0L) + safeMs;
        long monthMs = p.getLong("time_month_" + month, 0L) + safeMs;
        long trackMs = p.getLong("time_track_" + trackId, 0L) + safeMs;
        long trackDayMs = p.getLong("time_day_" + day + "_" + trackId, 0L) + safeMs;
        long trackWeekMs = p.getLong("time_week_" + week + "_" + trackId, 0L) + safeMs;
        long trackMonthMs = p.getLong("time_month_" + month + "_" + trackId, 0L) + safeMs;

        String sessionTracks = p.getString("session_tracks", "");
        long sessionStart = p.getLong("session_start", 0L);
        long sessionLast = p.getLong("session_last", 0L);
        long sessionDuration = p.getLong("session_duration", 0L);
        long nowMs = System.currentTimeMillis();

        SharedPreferences.Editor e = p.edit()
                .putLong("time_total_ms", total)
                .putLong("time_day_" + day, dayMs)
                .putLong("time_week_" + week, weekMs)
                .putLong("time_month_" + month, monthMs)
                .putLong("time_track_" + trackId, trackMs)
                .putLong("time_day_" + day + "_" + trackId, trackDayMs)
                .putLong("time_week_" + week + "_" + trackId, trackWeekMs)
                .putLong("time_month_" + month + "_" + trackId, trackMonthMs)
                .putString("artist_" + trackId, safe(artist));

        if (sessionStart == 0L || (sessionLast > 0L && nowMs - sessionLast > SESSION_GAP_MS)) {
            if (sessionStart > 0L && sessionDuration > 5000L) {
                appendSession(p, sessionStart, sessionDuration, sessionTracks);
            }
            sessionStart = nowMs;
            sessionDuration = 0L;
            sessionTracks = "";
        }
        String id = String.valueOf(trackId);
        Set<String> sessionSet = new HashSet<>();
        if (!sessionTracks.isEmpty()) {
            Collections.addAll(sessionSet, sessionTracks.split(","));
        }
        sessionSet.add(id);
        sessionTracks = join(sessionSet);
        sessionDuration += safeMs;

        e.putLong("session_start", sessionStart)
                .putLong("session_last", nowMs)
                .putLong("session_duration", sessionDuration)
                .putString("session_tracks", sessionTracks)
                .apply();

        ensureStreak(p, now);
    }

    public static Summary summary(Context context) {
        SharedPreferences p = prefs(context);
        Calendar now = Calendar.getInstance();
        return new Summary(
                p.getLong("time_total_ms", 0L),
                p.getLong("time_day_" + dayKey(now), 0L),
                p.getLong("time_week_" + weekKey(now), 0L),
                p.getLong("time_month_" + monthKey(now), 0L),
                p.getInt("plays_total", 0),
                p.getInt("plays_day_" + dayKey(now), 0),
                p.getInt("plays_week_" + weekKey(now), 0),
                p.getInt("plays_month_" + monthKey(now), 0),
                p.getInt("streak_current", 0),
                p.getInt("streak_best", 0)
        );
    }

    public static long trackTime(Context context, long trackId) {
        return prefs(context).getLong("time_track_" + trackId, 0L);
    }

    public static int hourScore(Context context, long trackId, int hour) {
        return prefs(context).getInt("plays_hour_" + Math.max(0, Math.min(23, hour)) + "_" + trackId, 0);
    }

    public static int trackPlayCount(Context context, long trackId) {
        return prefs(context).getInt("plays_track_" + trackId, 0);
    }

    public static int uniqueArtists(Context context) {
        return prefs(context).getStringSet("unique_artists", new HashSet<>()).size();
    }

    public static int nightPlays(Context context) {
        return prefs(context).getInt("night_plays", 0);
    }

    public static List<TrackScore> topTracks(Context context, List<MainActivity.TrackInfo> tracks,
                                              String period, int limit) {
        SharedPreferences p = prefs(context);
        Calendar now = Calendar.getInstance();
        String key = period == null ? "all" : period;
        List<TrackScore> result = new ArrayList<>();
        for (MainActivity.TrackInfo t : tracks) {
            long ms;
            if ("today".equals(key)) ms = p.getLong("time_day_" + dayKey(now) + "_" + t.id, 0L);
            else if ("week".equals(key)) ms = p.getLong("time_week_" + weekKey(now) + "_" + t.id, 0L);
            else if ("month".equals(key)) ms = p.getLong("time_month_" + monthKey(now) + "_" + t.id, 0L);
            else ms = p.getLong("time_track_" + t.id, 0L);
            if (ms > 0L) result.add(new TrackScore(t.id, t.title, t.artist, ms));
        }
        result.sort((a, b) -> Long.compare(b.timeMs, a.timeMs));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    public static List<TrackScore> topTracks(Context context, List<MainActivity.TrackInfo> tracks, int limit) {
        List<TrackScore> result = new ArrayList<>();
        for (MainActivity.TrackInfo t : tracks) {
            long ms = trackTime(context, t.id);
            if (ms > 0L) result.add(new TrackScore(t.id, t.title, t.artist, ms));
        }
        result.sort((a, b) -> Long.compare(b.timeMs, a.timeMs));
        if (result.size() > limit) return new ArrayList<>(result.subList(0, limit));
        return result;
    }

    public static List<AggregateScore> aggregateByArtist(Context context, List<MainActivity.TrackInfo> tracks, int limit) {
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        for (MainActivity.TrackInfo t : tracks) {
            String key = safe(t.artist);
            map.put(key, map.getOrDefault(key, 0L) + trackTime(context, t.id));
        }
        return sortedAggregates(map, limit);
    }

    public static List<AggregateScore> aggregateByAlbum(Context context, List<MainActivity.TrackInfo> tracks, int limit) {
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        for (MainActivity.TrackInfo t : tracks) {
            String key = safe(t.album);
            map.put(key, map.getOrDefault(key, 0L) + trackTime(context, t.id));
        }
        return sortedAggregates(map, limit);
    }

    public static List<AggregateScore> aggregateByGenre(Context context, List<MainActivity.TrackInfo> tracks, int limit) {
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        for (MainActivity.TrackInfo t : tracks) {
            String key = safe(t.genre);
            if (key.isEmpty()) key = "Género não informado";
            map.put(key, map.getOrDefault(key, 0L) + trackTime(context, t.id));
        }
        return sortedAggregates(map, limit);
    }

    public static List<Session> sessions(Context context) {
        SharedPreferences p = prefs(context);
        List<Session> out = new ArrayList<>();
        String raw = p.getString("sessions", "");
        if (!raw.isEmpty()) {
            for (String line : raw.split("\n")) {
                String[] parts = line.split("\\|", 3);
                if (parts.length != 3) continue;
                try {
                    out.add(new Session(Long.parseLong(parts[0]), Long.parseLong(parts[1]), parts[2]));
                } catch (NumberFormatException ignored) {}
            }
        }
        long start = p.getLong("session_start", 0L);
        long duration = p.getLong("session_duration", 0L);
        if (start > 0L && duration > 0L) out.add(0, new Session(start, duration, p.getString("session_tracks", "")));
        return out;
    }

    private static void appendSession(SharedPreferences p, long start, long duration, String tracks) {
        String raw = p.getString("sessions", "");
        String line = start + "|" + duration + "|" + tracks;
        String combined = line + (raw.isEmpty() ? "" : "\n" + raw);
        String[] lines = combined.split("\n");
        StringBuilder keep = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 20); i++) {
            if (keep.length() > 0) keep.append('\n');
            keep.append(lines[i]);
        }
        p.edit().putString("sessions", keep.toString()).apply();
    }

    private static void ensureStreak(SharedPreferences p, Calendar now) {
        String today = dayKey(now);
        String last = p.getString("streak_last_day", "");
        if (today.equals(last)) return;

        int current = p.getInt("streak_current", 0);
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        String y = dayKey(yesterday);
        current = y.equals(last) ? current + 1 : 1;
        p.edit()
                .putString("streak_last_day", today)
                .putInt("streak_current", current)
                .putInt("streak_best", Math.max(p.getInt("streak_best", 0), current))
                .apply();
    }

    private static List<AggregateScore> sortedAggregates(java.util.Map<String, Long> map, int limit) {
        List<AggregateScore> list = new ArrayList<>();
        for (java.util.Map.Entry<String, Long> e : map.entrySet()) {
            if (e.getValue() > 0L) list.add(new AggregateScore(e.getKey(), e.getValue()));
        }
        list.sort((a, b) -> Long.compare(b.timeMs, a.timeMs));
        if (list.size() > limit) return new ArrayList<>(list.subList(0, limit));
        return list;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static String join(Set<String> values) {
        StringBuilder b = new StringBuilder();
        for (String s : values) {
            if (b.length() > 0) b.append(',');
            b.append(s);
        }
        return b.toString();
    }

    private static String dayKey(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static String weekKey(Calendar c) {
        Calendar copy = (Calendar) c.clone();
        copy.setFirstDayOfWeek(Calendar.MONDAY);
        copy.setMinimalDaysInFirstWeek(4);
        return String.format(Locale.US, "%04d-%02d", copy.getWeekYear(), copy.get(Calendar.WEEK_OF_YEAR));
    }

    private static String monthKey(Calendar c) {
        return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    public static String formatDuration(long ms) {
        long minutes = Math.max(0L, ms / 60000L);
        if (minutes < 60L) return minutes + " min";
        long hours = minutes / 60L;
        return hours + "h " + (minutes % 60L) + "min";
    }

    public static final class Summary {
        public final long totalMs, todayMs, weekMs, monthMs;
        public final int totalPlays, todayPlays, weekPlays, monthPlays, streakCurrent, streakBest;
        Summary(long totalMs, long todayMs, long weekMs, long monthMs,
                int totalPlays, int todayPlays, int weekPlays, int monthPlays,
                int streakCurrent, int streakBest) {
            this.totalMs = totalMs; this.todayMs = todayMs; this.weekMs = weekMs; this.monthMs = monthMs;
            this.totalPlays = totalPlays; this.todayPlays = todayPlays; this.weekPlays = weekPlays; this.monthPlays = monthPlays;
            this.streakCurrent = streakCurrent; this.streakBest = streakBest;
        }
    }

    public static final class TrackScore {
        public final long id; public final String title, artist; public final long timeMs;
        TrackScore(long id, String title, String artist, long timeMs) {
            this.id = id; this.title = title; this.artist = artist; this.timeMs = timeMs;
        }
    }

    public static final class AggregateScore {
        public final String name; public final long timeMs;
        AggregateScore(String name, long timeMs) { this.name = name; this.timeMs = timeMs; }
    }

    public static final class Session {
        public final long startMs, durationMs; public final String trackIds;
        Session(long startMs, long durationMs, String trackIds) {
            this.startMs = startMs; this.durationMs = durationMs; this.trackIds = trackIds;
        }
    }
}
