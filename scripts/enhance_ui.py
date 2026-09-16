from pathlib import Path
import re

path = Path("app/src/main/java/com/auren/musicplayer/MainActivity.java")
text = path.read_text()

# Required imports used by the current UI.
required_imports = [
    "import android.app.Dialog;",
    "import android.widget.ImageButton;",
    "import android.widget.ImageView;",
    "import android.widget.PopupMenu;",
    "import android.widget.HorizontalScrollView;",
    "import android.view.WindowManager;",
    "import java.util.HashSet;",
    "import java.util.Set;",
]
for imp in required_imports:
    if imp not in text:
        if imp.startswith("import java."):
            anchor = "import java.util.Map;"
        elif "PopupMenu" in imp:
            anchor = "import android.widget.ImageButton;"
        else:
            anchor = "import android.app.AlertDialog;"
        text = text.replace(anchor, anchor + "\n" + imp, 1)

text = text.replace(
    "if (currentTrack != null) showNowPlaying();",
    "if (currentTrack != null) openNowPlaying();",
)
text = text.replace(
    "item.setBackground(roundDrawable(Color.WHITE, 16));",
    "item.setBackgroundColor(Color.WHITE);",
)


def replace_method(source, method_name, replacement):
    match = re.search(
        r"(?m)^\s*private\s+(?:[\w<>]+)\s+"
        + re.escape(method_name)
        + r"\s*\([^)]*\)\s*\{",
        source,
    )
    if not match:
        return source, False
    brace_start = source.find("{", match.start())
    depth = 0
    for index in range(brace_start, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return (
                    source[:match.start()]
                    + replacement.rstrip()
                    + "\n"
                    + source[index + 1:],
                    True,
                )
    return source, False

# Make MediaStore duration available to every Track so playlist totals are real.
old_projection = '''        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID
        };'''
new_projection = '''        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION
        };'''
text = text.replace(old_projection, new_projection)

old_track = '''                    tracks.add(new Track(
                            cursor.getLong(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getLong(3)));'''
new_track = '''                    tracks.add(new Track(
                            cursor.getLong(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getLong(3),
                            cursor.getLong(4)));'''
text = text.replace(old_track, new_track)

# Real playlist screen: every user-created playlist opens its own page.
playlist_method = '''    private void showPlaylists() {
        setActiveTab(playlistTab);
        pageContainer.removeAllViews();
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(18));

        TextView eyebrow = text("YOUR COLLECTION", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Playlists", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);
        content.addView(title, margins(0, 3, 0, 0));

        LinearLayout create = rounded(0xFFEEECFF, 20);
        create.setGravity(Gravity.CENTER_VERTICAL);
        TextView plus = text("+", 28, R.color.auren_primary);
        plus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        plus.setGravity(Gravity.CENTER);
        create.addView(plus, new LinearLayout.LayoutParams(dp(54), dp(62)));
        LinearLayout createText = column();
        TextView ct = text("Criar playlist", 15, R.color.text_primary);
        ct.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        createText.addView(ct);
        createText.addView(text("Organize suas músicas", 12, R.color.text_secondary));
        create.addView(createText, new LinearLayout.LayoutParams(0, dp(62), 1));
        create.setOnClickListener(v -> showCreatePlaylistDialog());
        content.addView(create, margins(0, 18, 0, 12));

        content.addView(playlistCard("Liked songs", "Songs you marked as favorite", countFavorites(), true));
        content.addView(playlistCard("Recently played", "Your latest listening history", recentTracks.size(), false), margins(0, 10, 0, 0));
        content.addView(playlistCard("Most played", "The tracks you play the most", Math.min(10, tracks.size()), false), margins(0, 10, 0, 0));

        Set<String> names = getSharedPreferences("auren_player", MODE_PRIVATE)
                .getStringSet("playlist_names", new HashSet<>());
        if (names.isEmpty()) {
            content.addView(emptyCard("Crie uma playlist e ela aparecerá aqui."), margins(0, 14, 0, 0));
        } else {
            TextView yours = text("SUAS PLAYLISTS", 11, R.color.auren_primary);
            yours.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            content.addView(yours, margins(0, 18, 0, 8));
            for (String name : names) {
                content.addView(customPlaylistCard(name), margins(0, 0, 0, 10));
            }
        }
        pageContainer.addView(content);
    }
'''
text, changed = replace_method(text, 'showPlaylists', playlist_method)
if not changed:
    raise SystemExit('Could not update showPlaylists')

# Dedicated playlist page with count, total duration and playable songs.
playlist_helpers = '''    private View customPlaylistCard(String name) {
        List<Track> items = getPlaylistTracks(name);
        long total = playlistDuration(items);

        LinearLayout card = rounded(Color.WHITE, 18);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setElevation(dp(2));

        LinearLayout cover = rounded(0xFFEEECFF, 16);
        cover.setGravity(Gravity.CENTER);
        TextView icon = text("♫", 28, R.color.auren_primary);
        icon.setGravity(Gravity.CENTER);
        cover.addView(icon, new LinearLayout.LayoutParams(dp(62), dp(62)));
        card.addView(cover, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout info = column();
        info.setPadding(dp(12), 0, dp(8), 0);
        TextView title = text(name, 16, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);
        info.addView(text(items.size() + " músicas • " + formatDuration(total), 12, R.color.text_secondary), margins(0, 4, 0, 0));
        info.addView(text("Toque para abrir", 11, R.color.auren_primary), margins(0, 4, 0, 0));
        card.addView(info, new LinearLayout.LayoutParams(0, dp(78), 1));

        TextView arrow = text("›", 28, R.color.text_secondary);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(70)));
        card.setOnClickListener(v -> showPlaylistPage(name));
        return card;
    }

    private void showPlaylistPage(String name) {
        setActiveTab(playlistTab);
        pageContainer.removeAllViews();

        LinearLayout content = column();
        content.setPadding(dp(20), dp(14), dp(20), dp(18));

        LinearLayout header = row();
        ImageButton back = iconButton(android.R.drawable.ic_media_previous, "Voltar para playlists");
        back.setOnClickListener(v -> showPlaylists());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = column();
        TextView eyebrow = text("PLAYLIST", 10, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titles.addView(eyebrow);
        TextView title = text(name, 25, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(title, margins(0, 2, 0, 0));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(header);

        List<Track> items = getPlaylistTracks(name);
        long total = playlistDuration(items);
        LinearLayout summary = rounded(0xFFEEECFF, 18);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView count = text(items.size() + " músicas", 13, R.color.text_primary);
        count.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        summary.addView(count, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView duration = text(formatDuration(total), 13, R.color.auren_primary);
        duration.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        duration.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        summary.addView(duration, new LinearLayout.LayoutParams(dp(90), dp(40)));
        content.addView(summary, margins(0, 14, 0, 12));

        if (items.isEmpty()) {
            content.addView(emptyCard("Esta playlist está vazia. Use ⋮ ao lado de uma música e escolha Adicionar à playlist."));
        } else {
            TextView songs = text("MÚSICAS", 11, R.color.auren_primary);
            songs.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            content.addView(songs, margins(0, 4, 0, 8));
            ScrollView scroll = new ScrollView(this);
            LinearLayout list = column();
            for (Track track : items) list.addView(trackRow(track, 0));
            scroll.addView(list);
            content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        pageContainer.addView(content, new LinearLayout.LayoutParams(-1, -1));
    }

    private List<Track> getPlaylistTracks(String name) {
        List<Track> result = new ArrayList<>();
        String value = getSharedPreferences("auren_player", MODE_PRIVATE)
                .getString("playlist_" + name, "");
        if (value == null || value.trim().isEmpty()) return result;
        for (String idValue : value.split(",")) {
            try {
                long id = Long.parseLong(idValue.trim());
                for (Track track : tracks) {
                    if (track.id == id) {
                        result.add(track);
                        break;
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private long playlistDuration(List<Track> items) {
        long total = 0;
        for (Track track : items) total += Math.max(0, track.durationMs);
        return total;
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "0:00";
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        minutes %= 60;
        seconds %= 60;
        if (hours > 0) return hours + ":" + String.format(Locale.US, "%02d:%02d", minutes, seconds);
        return minutes + ":" + String.format(Locale.US, "%02d", seconds);
    }

'''
if 'private View customPlaylistCard(' not in text:
    marker = '    private void showMostPlayed() {'
    insertion = text.find(marker)
    if insertion < 0:
        raise SystemExit('Could not find playlist helper insertion point')
    text = text[:insertion] + playlist_helpers + text[insertion:]

# Store duration in Track.
track_class = '''    private static class Track {
        final long id;
        final String title;
        final String artist;
        final long albumId;

        Track(long id, String title, String artist, long albumId) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.albumId = albumId;
        }
'''
new_track_class = '''    private static class Track {
        final long id;
        final String title;
        final String artist;
        final long albumId;
        final long durationMs;

        Track(long id, String title, String artist, long albumId, long durationMs) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.albumId = albumId;
            this.durationMs = durationMs;
        }
'''
if track_class in text:
    text = text.replace(track_class, new_track_class, 1)

# The row already has the three-dot menu; ensure the unfinished text never returns.
if 'More player options coming soon.' in text:
    raise SystemExit('Unfinished player options message remains')
for forbidden in ('showNowPlaying();', 'roundDrawable('):
    if forbidden in text:
        raise SystemExit(f'Unresolved UI enhancement reference: {forbidden}')

path.write_text(text)
print('Auren UI enhancement completed: playlist pages, playlist duration and navigation.')
