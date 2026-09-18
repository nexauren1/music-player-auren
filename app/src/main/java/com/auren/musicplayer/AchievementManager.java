package com.auren.musicplayer;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AchievementManager {
    private static final String PREFS = "auren_achievements";
    private static final String TOTAL = "total_plays";
    private static final String UNIQUE = "unique_tracks";
    private static final String DAYS = "active_days";
    private static final WeekFields ISO = WeekFields.ISO;
    private static final long EVENT_DEDUP_MS = 1500L;

    private AchievementManager() {}

    public static synchronized void recordPlay(
            Context context,
            long trackId) {

        if (trackId <= 0) return;

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE);

        long now = System.currentTimeMillis();
        long lastAt = prefs.getLong(
                "last_event_at",
                0L);
        long lastTrack = prefs.getLong(
                "last_event_track",
                -1L);

        if (lastTrack == trackId
                && now - lastAt < EVENT_DEDUP_MS) {
            return;
        }

        LocalDate today = LocalDate.now();

        int total = prefs.getInt(
                TOTAL,
                0) + 1;
        int day = prefs.getInt(
                dayKey(today),
                0) + 1;
        int week = prefs.getInt(
                weekKey(today),
                0) + 1;
        int month = prefs.getInt(
                monthKey(today),
                0) + 1;

        Set<String> unique = new HashSet<>(
                prefs.getStringSet(
                        UNIQUE,
                        new HashSet<>()));

        unique.add(
                String.valueOf(trackId));

        Set<String> activeDays = new HashSet<>(
                prefs.getStringSet(
                        DAYS,
                        new HashSet<>()));

        activeDays.add(
                today.toString());

        prefs.edit()
                .putInt(
                        TOTAL,
                        total)
                .putStringSet(
                        UNIQUE,
                        unique)
                .putStringSet(
                        DAYS,
                        activeDays)
                .putInt(
                        dayKey(today),
                        day)
                .putInt(
                        weekKey(today),
                        week)
                .putInt(
                        monthKey(today),
                        month)
                .putInt(
                        "record_day",
                        Math.max(
                                prefs.getInt(
                                        "record_day",
                                        0),
                                day))
                .putInt(
                        "record_week",
                        Math.max(
                                prefs.getInt(
                                        "record_week",
                                        0),
                                week))
                .putInt(
                        "record_month",
                        Math.max(
                                prefs.getInt(
                                        "record_month",
                                        0),
                                month))
                .putLong(
                        "last_event_track",
                        trackId)
                .putLong(
                        "last_event_at",
                        now)
                .apply();
    }

    public static Stats stats(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE);

        LocalDate today =
                LocalDate.now();

        int total =
                prefs.getInt(TOTAL, 0);

        int unique =
                prefs.getStringSet(
                        UNIQUE,
                        new HashSet<>())
                        .size();

        int days =
                prefs.getStringSet(
                        DAYS,
                        new HashSet<>())
                        .size();

        List<Badge> badges =
                badges(context);

        int unlocked = 0;

        for (Badge badge : badges) {
            if (badge.unlocked) {
                unlocked++;
            }
        }

        return new Stats(
                prefs.getInt(
                        dayKey(today),
                        0),
                prefs.getInt(
                        weekKey(today),
                        0),
                prefs.getInt(
                        monthKey(today),
                        0),
                prefs.getInt(
                        "record_day",
                        0),
                prefs.getInt(
                        "record_week",
                        0),
                prefs.getInt(
                        "record_month",
                        0),
                total,
                unique,
                days,
                unlocked,
                badges.size());
    }

    public static List<Badge> badges(
            Context context) {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE);

        LocalDate today =
                LocalDate.now();

        int total =
                prefs.getInt(TOTAL, 0);

        int unique =
                prefs.getStringSet(
                        UNIQUE,
                        new HashSet<>())
                        .size();

        int days =
                prefs.getStringSet(
                        DAYS,
                        new HashSet<>())
                        .size();

        int month =
                prefs.getInt(
                        monthKey(today),
                        0);

        int bestDay =
                prefs.getInt(
                        "record_day",
                        0);

        int bestWeek =
                prefs.getInt(
                        "record_week",
                        0);

        int bestMonth =
                prefs.getInt(
                        "record_month",
                        0);

        long totalMs =
                AurenAnalytics.summary(
                        context).totalMs;

        int artists =
                AurenAnalytics.uniqueArtists(
                        context);

        boolean sevenDayStreak =
                AurenAnalytics.summary(
                        context).streakBest >= 7;

        boolean thirtyDayStreak =
                AurenAnalytics.summary(
                        context).streakBest >= 30;

        List<Badge> result =
                new ArrayList<>();

        result.add(new Badge(
                "first",
                "Primeira faixa",
                "Toque a sua primeira música.",
                "1 reprodução",
                total >= 1));

        result.add(new Badge(
                "ten",
                "Primeiro ritmo",
                "Chegue a 10 reproduções.",
                "10",
                total >= 10));

        result.add(new Badge(
                "explorer",
                "Explorador",
                "Ouça 10 músicas diferentes.",
                "10 músicas",
                unique >= 10));

        result.add(new Badge(
                "fifty",
                "Maratonista",
                "Chegue a 50 reproduções.",
                "50",
                total >= 50));

        result.add(new Badge(
                "week",
                "Semana musical",
                "Tenha atividade em 7 dias.",
                "7 dias",
                days >= 7));

        result.add(new Badge(
                "month",
                "Centena mensal",
                "Alcance 100 reproduções no mês atual.",
                "100 no mês",
                month >= 100));

        result.add(new Badge(
                "first_day",
                "Primeira música do dia",
                "Comece o dia com uma reprodução.",
                "1 hoje",
                prefs.getInt(
                        dayKey(today),
                        0) >= 1));

        result.add(new Badge(
                "minutes",
                "100 minutos hoje",
                "Ouça pelo menos 100 minutos num dia.",
                "100 min",
                AurenAnalytics.summary(
                        context).todayMs
                        >= 100L * 60_000L));

        result.add(new Badge(
                "streak",
                "Sete dias seguidos",
                "Ouça música em sete dias consecutivos.",
                "7 dias",
                sevenDayStreak));

        result.add(new Badge(
                "artists",
                "10 artistas",
                "Ouça músicas de 10 artistas diferentes.",
                "10 artistas",
                artists >= 10));

        result.add(new Badge(
                "night",
                "Sessão noturna",
                "Tenha uma reprodução entre 20:00 e 05:00.",
                "1 noite",
                AurenAnalytics.nightPlays(
                        context) >= 1));

        result.add(new Badge(
                "repeat",
                "Em loop",
                "Repita uma música pelo menos cinco vezes.",
                "5 vezes",
                hasRepeatedTrack(context)));

        result.add(new Badge(
                "album",
                "Álbum completo",
                "Ouça todas as faixas de um álbum com pelo menos duas músicas.",
                "álbum 100%",
                context.getSharedPreferences(
                        "auren_player",
                        Context.MODE_PRIVATE)
                        .getBoolean(
                                "album_complete",
                                false)));

        result.add(new Badge(
                "daily_record",
                "Dia de recorde",
                "Faça pelo menos 20 reproduções num dia.",
                "20 num dia",
                bestDay >= 20));

        result.add(new Badge(
                "weekly_record",
                "Semana de recorde",
                "Faça pelo menos 50 reproduções numa semana.",
                "50 na semana",
                bestWeek >= 50));

        result.add(new Badge(
                "monthly_record",
                "Mês de recorde",
                "Faça pelo menos 150 reproduções num mês.",
                "150 no mês",
                bestMonth >= 150));

        result.add(new Badge(
                "five_hundred",
                "Quinhentas",
                "Chegue a 500 reproduções.",
                "500",
                total >= 500));

        result.add(new Badge(
                "thousand_minutes",
                "1000 minutos",
                "Passe 1000 minutos de audição.",
                "1000 min",
                totalMs >= 1000L * 60_000L));

        result.add(new Badge(
                "thirty_days",
                "Habitual",
                "Tenha atividade em pelo menos 30 dias.",
                "30 dias",
                days >= 30));

        result.add(new Badge(
                "twenty_artists",
                "Explorador de artistas",
                "Ouça 20 artistas diferentes.",
                "20 artistas",
                artists >= 20));

        result.add(new Badge(
                "thirty_streak",
                "Mês de consistência",
                "Mantenha uma sequência de 30 dias.",
                "30 dias seguidos",
                thirtyDayStreak));

        return result;
    }

    private static boolean hasRepeatedTrack(
            Context context) {

        SharedPreferences p =
                context.getSharedPreferences(
                        "auren_player",
                        Context.MODE_PRIVATE);

        String raw =
                p.getString(
                        "play_counts_v2",
                        "");

        if (raw != null) {
            for (String entry :
                    raw.split(";")) {

                String[] pair =
                        entry.split("=", 2);

                if (pair.length == 2) {
                    try {
                        if (Integer.parseInt(
                                pair[1]) >= 5) {
                            return true;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return false;
    }

    public static void clear(Context context) {
        context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    private static String dayKey(
            LocalDate date) {
        return "day_" + date;
    }

    private static String weekKey(
            LocalDate date) {
        return String.format(
                Locale.US,
                "week_%d_%02d",
                date.get(
                        ISO.weekBasedYear()),
                date.get(
                        ISO.weekOfWeekBasedYear()));
    }

    private static String monthKey(
            LocalDate date) {
        return "month_"
                + date.toString()
                .substring(0, 7);
    }

    public static final class Stats {
        public final int today;
        public final int week;
        public final int month;
        public final int bestDay;
        public final int bestWeek;
        public final int bestMonth;
        public final int totalPlays;
        public final int uniqueTracks;
        public final int activeDays;
        public final int unlocked;
        public final int totalAchievements;

        Stats(
                int today,
                int week,
                int month,
                int bestDay,
                int bestWeek,
                int bestMonth,
                int totalPlays,
                int uniqueTracks,
                int activeDays,
                int unlocked,
                int totalAchievements) {

            this.today = today;
            this.week = week;
            this.month = month;
            this.bestDay = bestDay;
            this.bestWeek = bestWeek;
            this.bestMonth = bestMonth;
            this.totalPlays = totalPlays;
            this.uniqueTracks = uniqueTracks;
            this.activeDays = activeDays;
            this.unlocked = unlocked;
            this.totalAchievements = totalAchievements;
        }
    }

    public static final class Badge {
        public final String id;
        public final String title;
        public final String description;
        public final String requirement;
        public final boolean unlocked;

        Badge(
                String id,
                String title,
                String description,
                String requirement,
                boolean unlocked) {

            this.id = id;
            this.title = title;
            this.description = description;
            this.requirement = requirement;
            this.unlocked = unlocked;
        }
    }
}
