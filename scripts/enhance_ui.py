from pathlib import Path
import re

path = Path('app/src/main/java/com/auren/musicplayer/MainActivity.java')
text = path.read_text()

for imp, anchor in [
    ('import android.app.Dialog;', 'import android.app.AlertDialog;'),
    ('import android.widget.PopupMenu;', 'import android.widget.ImageButton;'),
    ('import android.widget.HorizontalScrollView;', 'import android.widget.ImageView;'),
    ('import android.view.WindowManager;', 'import android.view.Window;'),
    ('import java.util.HashSet;', 'import java.util.Set;'),
    ('import androidx.media3.common.PlaybackParameters;', 'import androidx.media3.common.MediaItem;'),
]:
    if imp not in text:
        text = text.replace(anchor, anchor + '\n' + imp, 1)

text = text.replace('if (currentTrack != null) showNowPlaying();',
                    'if (currentTrack != null) openNowPlaying();')
text = text.replace('item.setBackground(roundDrawable(Color.WHITE, 16));',
                    'item.setBackgroundColor(Color.WHITE);')
text = text.replace('More player options coming soon.', 'Player options')


def replace_method(source, name, replacement):
    match = re.search(r'(?m)^\s*private\s+[\w<>]+\s+' + re.escape(name) +
                      r'\s*\([^)]*\)\s*\{', source)
    if not match:
        return source, False
    brace = source.find('{', match.start())
    depth = 0
    for i in range(brace, len(source)):
        if source[i] == '{':
            depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0:
                return source[:match.start()] + replacement.rstrip() + '\n' + source[i + 1:], True
    return source, False

# Ensure MediaStore duration is present, without assuming an old Track constructor.
if 'MediaStore.Audio.Media.DURATION' not in text:
    old = '''        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID
        };'''
    new = '''        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION
        };'''
    text = text.replace(old, new, 1)

if 'final long durationMs;' not in text:
    text = text.replace(
        '        final long albumId;\n\n        Track(long id, String title, String artist, long albumId) {',
        '        final long albumId;\n        final long durationMs;\n\n        Track(long id, String title, String artist, long albumId, long durationMs) {',
        1)
    text = text.replace(
        '            this.albumId = albumId;\n        }\n\n        Uri albumArtUri()',
        '            this.albumId = albumId;\n            this.durationMs = durationMs;\n        }\n\n        Uri albumArtUri()',
        1)
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
    text = text.replace(old_track, new_track, 1)

# Keep the dedicated playlist screen available on every build.
if 'private View customPlaylistCard(String name)' not in text:
    helpers = '''    private View customPlaylistCard(String name) {
        List<Track> items = getPlaylistTracks(name);
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
        info.addView(text(items.size() + " músicas • " + formatDuration(playlistDuration(items)), 12, R.color.text_secondary), margins(0, 4, 0, 0));
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
        TextView title = text(name, 25, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        content.addView(header);
        List<Track> items = getPlaylistTracks(name);
        LinearLayout summary = rounded(0xFFEEECFF, 18);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = text(items.size() + " músicas", 13, R.color.text_primary);
        count.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        summary.addView(count, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView duration = text(formatDuration(playlistDuration(items)), 13, R.color.auren_primary);
        duration.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        duration.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        summary.addView(duration, new LinearLayout.LayoutParams(dp(90), dp(44)));
        content.addView(summary, margins(0, 14, 0, 12));
        LinearLayout actions = row();
        actions.addView(actionCard("▶", "Reproduzir", v -> playPlaylist(name, false)), new LinearLayout.LayoutParams(0, dp(68), 1));
        actions.addView(actionCard("⇄", "Aleatório", v -> playPlaylist(name, true)), margins(10, 0, 0, 0));
        content.addView(actions);
        if (items.isEmpty()) {
            content.addView(emptyCard("Esta playlist está vazia. Use ⋮ ao lado de uma música e escolha Adicionar à playlist."), margins(0, 16, 0, 0));
        } else {
            TextView songs = text("MÚSICAS", 11, R.color.auren_primary);
            songs.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            content.addView(songs, margins(0, 16, 0, 8));
            ScrollView scroll = new ScrollView(this);
            LinearLayout list = column();
            for (Track track : items) list.addView(trackRow(track, 0));
            scroll.addView(list);
            content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        pageContainer.addView(content, new LinearLayout.LayoutParams(-1, -1));
    }

    private void playPlaylist(String name, boolean shuffle) {
        List<Track> items = getPlaylistTracks(name);
        if (items.isEmpty()) {
            Toast.makeText(this, "Esta playlist está vazia.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (shuffle) Collections.shuffle(items);
        play(items.get(0));
        openNowPlaying();
    }

    private boolean isCustomPlaylist(String name) {
        return getSharedPreferences("auren_player", MODE_PRIVATE)
                .getStringSet("playlist_names", new HashSet<>()).contains(name);
    }

    private List<Track> getPlaylistTracks(String name) {
        List<Track> result = new ArrayList<>();
        String value = getSharedPreferences("auren_player", MODE_PRIVATE).getString("playlist_" + name, "");
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
    pos = text.find('    private void showMostPlayed()')
    if pos < 0:
        raise SystemExit('showMostPlayed marker not found')
    text = text[:pos] + helpers + text[pos:]

old_card = '        if (favorite) card.setOnClickListener(v -> showLibrary(true));\n        return card;'
new_card = '        if (favorite) card.setOnClickListener(v -> showLibrary(true));\n        else if (isCustomPlaylist(title)) card.setOnClickListener(v -> showPlaylistPage(title));\n        return card;'
text = text.replace(old_card, new_card, 1)

# Add the effects menu directly to the mini player. Values are kept in the activity
# so they remain active while navigating between pages.
if 'private float playbackSpeed = 1.0f;' not in text:
    marker = '    private boolean userDragging;'
    text = text.replace(marker, marker + '\n    private float playbackSpeed = 1.0f;\n    private float playbackPitch = 1.0f;', 1)

if 'private void showMiniPlayerMenu(View anchor)' not in text:
    effects = '''    private void showMiniPlayerMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Efeitos");
        popup.getMenu().add("Velocidade: " + formatEffectValue(playbackSpeed));
        popup.getMenu().add("Pitch: " + formatEffectValue(playbackPitch));
        popup.getMenu().add("Repor efeitos");
        popup.setOnMenuItemClickListener(item -> {
            String action = item.getTitle().toString();
            if (action.equals("Efeitos")) {
                showEffectsDialog();
            } else if (action.startsWith("Velocidade:")) {
                showSpeedDialog();
            } else if (action.startsWith("Pitch:")) {
                showPitchDialog();
            } else if (action.equals("Repor efeitos")) {
                playbackSpeed = 1.0f;
                playbackPitch = 1.0f;
                applyPlaybackEffects();
                Toast.makeText(this, "Efeitos repostos.", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
    }

    private String formatEffectValue(float value) {
        return String.format(Locale.US, "%.2fx", value);
    }

    private void applyPlaybackEffects() {
        if (player != null) {
            player.setPlaybackParameters(new PlaybackParameters(playbackSpeed, playbackPitch));
        }
    }

    private void showEffectsDialog() {
        final String[] options = {
                "Velocidade — " + formatEffectValue(playbackSpeed),
                "Pitch — " + formatEffectValue(playbackPitch),
                "Repor velocidade e pitch"
        };
        new AlertDialog.Builder(this)
                .setTitle("Efeitos de reprodução")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showSpeedDialog();
                    else if (which == 1) showPitchDialog();
                    else {
                        playbackSpeed = 1.0f;
                        playbackPitch = 1.0f;
                        applyPlaybackEffects();
                    }
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showSpeedDialog() {
        final float[] values = {0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 1.75f, 2.00f};
        final String[] labels = {"0.50x", "0.75x", "1.00x Normal", "1.25x", "1.50x", "1.75x", "2.00x"};
        int checked = 2;
        for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - playbackSpeed) < 0.01f) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("Velocidade")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    playbackSpeed = values[which];
                    applyPlaybackEffects();
                    dialog.dismiss();
                    Toast.makeText(this, "Velocidade: " + labels[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showPitchDialog() {
        final float[] values = {0.75f, 0.85f, 0.95f, 1.00f, 1.05f, 1.15f, 1.25f};
        final String[] labels = {"-5 semitons", "-3 semitons", "-1 semitom", "Normal", "+1 semitom", "+3 semitons", "+5 semitons"};
        int checked = 3;
        for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - playbackPitch) < 0.01f) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("Pitch")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    playbackPitch = values[which];
                    applyPlaybackEffects();
                    dialog.dismiss();
                    Toast.makeText(this, "Pitch: " + labels[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

'''
    marker = '    private void closeNowPlaying() {'
    pos = text.find(marker)
    if pos < 0:
        raise SystemExit('closeNowPlaying marker not found')
    text = text[:pos] + effects + text[pos:]

# Make the mini player have a three-dot functions button.
old_mini = '''        ImageButton next = iconButton(android.R.drawable.ic_media_next, "Next song");
        next.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        DrawableCompat.setTint(next.getDrawable(), getColor(R.color.text_primary));
        next.setOnClickListener(v -> nextTrackInPlayer());
        row.addView(next, new LinearLayout.LayoutParams(dp(42), dp(52)));

        miniPlay = iconButton(android.R.drawable.ic_media_play, "Play or pause");'''
new_mini = '''        ImageButton functions = iconButton(android.R.drawable.ic_menu_more, "Funções e efeitos");
        functions.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        DrawableCompat.setTint(functions.getDrawable(), getColor(R.color.text_primary));
        functions.setOnClickListener(v -> showMiniPlayerMenu(functions));
        row.addView(functions, new LinearLayout.LayoutParams(dp(38), dp(52)));

        ImageButton next = iconButton(android.R.drawable.ic_media_next, "Next song");
        next.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        DrawableCompat.setTint(next.getDrawable(), getColor(R.color.text_primary));
        next.setOnClickListener(v -> nextTrackInPlayer());
        row.addView(next, new LinearLayout.LayoutParams(dp(42), dp(52)));

        miniPlay = iconButton(android.R.drawable.ic_media_play, "Play or pause");'''
text = text.replace(old_mini, new_mini, 1)

# Also expose the same effects from the full player's options menu.
needle = '            popup.getMenu().add("Aleatório");\n            popup.getMenu().add("Fechar reprodução");'
replacement = '            popup.getMenu().add("Aleatório");\n            popup.getMenu().add("Efeitos: velocidade e pitch");\n            popup.getMenu().add("Fechar reprodução");'
text = text.replace(needle, replacement, 1)
text = text.replace('                } else {\n                    closeNowPlaying();\n                }', '                } else if (action.startsWith("Efeitos:")) {\n                    showEffectsDialog();\n                } else {\n                    closeNowPlaying();\n                }', 1)

for forbidden in ('More player options coming soon.', 'showNowPlaying();', 'roundDrawable('):
    if forbidden in text:
        raise SystemExit('Unfinished/unresolved reference remains: ' + forbidden)

path.write_text(text)
print('Auren effects ready: mini-player functions menu with speed and pitch controls.')