from pathlib import Path
import re

path = Path("app/src/main/java/com/auren/musicplayer/MainActivity.java")
text = path.read_text()

required_imports = [
    "import android.app.Dialog;",
    "import android.widget.ImageButton;",
    "import android.widget.ImageView;",
    "import android.widget.HorizontalScrollView;",
    "import android.widget.PopupMenu;",
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

# Remove any unfinished Now Playing action from older versions.
text = text.replace(
    'ImageButton more = iconButton(android.R.drawable.ic_menu_more, "More options");\n'
    '        more.setOnClickListener(v -> Toast.makeText(this, "More player options coming soon.", Toast.LENGTH_SHORT).show());\n'
    '        top.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));',
    'ImageButton more = iconButton(android.R.drawable.btn_star_big_off, "Favorite");\n'
    '        more.setOnClickListener(v -> {\n'
    '            setFavorite(currentTrack, !isFavorite(currentTrack));\n'
    '            more.setImageResource(isFavorite(currentTrack) ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);\n'
    '            updateMiniPlayer();\n'
    '        });\n'
    '        top.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));',
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

# Keep the polished Home hero and the complete local library on Home.
hero_call = '        content.addView(buildHomeHero(), margins(0, 8, 0, 0));\n'
if 'content.addView(buildHomeHero()' not in text:
    marker = '        content.addView(header);\n'
    text = text.replace(marker, marker + '\n' + hero_call, 1)

hero_method = '''    private View buildHomeHero() {
        LinearLayout card = rounded(getColor(R.color.auren_primary), 26);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setElevation(dp(4));

        LinearLayout media = row();
        media.setGravity(Gravity.CENTER_VERTICAL);

        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (miniArt != null && miniArt.getDrawable() != null) {
            art.setImageDrawable(miniArt.getDrawable());
        } else {
            art.setImageResource(android.R.drawable.ic_media_play);
            if (art.getDrawable() != null) DrawableCompat.setTint(art.getDrawable(), Color.WHITE);
        }
        media.addView(art, new LinearLayout.LayoutParams(dp(88), dp(88)));

        LinearLayout info = column();
        info.setPadding(dp(14), 0, 0, 0);
        TextView eyebrow = text("AGORA NO AUREN", 10, android.R.color.white);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(eyebrow);
        String heroTitle = miniTitle == null ? "A música move você" : miniTitle.getText().toString();
        if (heroTitle.trim().isEmpty()) heroTitle = "A música move você";
        TextView title = text(heroTitle, 20, android.R.color.white);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        info.addView(title, margins(0, 4, 0, 2));
        String heroArtist = miniArtist == null ? "Descubra, ouça e aproveite" : miniArtist.getText().toString();
        if (heroArtist.trim().isEmpty()) heroArtist = "Descubra, ouça e aproveite";
        TextView artist = text(heroArtist, 12, android.R.color.white);
        artist.setMaxLines(1);
        info.addView(artist);
        TextView action = text("Abrir reprodução  ›", 12, android.R.color.white);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(action, margins(0, 10, 0, 0));
        media.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(media);
        card.setOnClickListener(v -> { if (currentTrack != null) openNowPlaying(); });
        return card;
    }
'''
if 'private View buildHomeHero()' not in text:
    insertion = text.find('    private void showHome()')
    if insertion >= 0:
        text = text[:insertion] + hero_method + '\n' + text[insertion:]

all_songs_block = '''        // AUREN_ALL_SONGS_HOME_START
        addSectionHeader(content, "Todas as músicas", tracks.size() + " músicas", v -> showLibrary());
        if (tracks.isEmpty()) {
            content.addView(emptyCard("Nenhuma música encontrada no dispositivo."));
        } else {
            for (Track track : tracks) content.addView(trackRow(track, 0));
        }
        // AUREN_ALL_SONGS_HOME_END

'''
if 'AUREN_ALL_SONGS_HOME_START' not in text:
    marker = '        addSectionHeader(content, "Suggestions for you", "Refresh", v -> showHome());\n'
    text = text.replace(marker, all_songs_block + marker, 1)

# Functional playlist page: creation is persisted and playlists can be opened.
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
        for (String name : names) {
            content.addView(playlistCard(name, "Playlist criada por você", playlistTrackCount(name), false), margins(0, 10, 0, 0));
        }
        pageContainer.addView(content);
    }
'''
text, changed = replace_method(text, 'showPlaylists', playlist_method)
if not changed:
    raise SystemExit('Could not update showPlaylists')

# Add three-dot action menu to every song row, matching the reference layout.
track_method = '''    private View trackRow(Track track, int number) {
        LinearLayout row = rounded(0xFFFFFFFF, 16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(7), dp(4), dp(7));

        ImageView art = artwork(48);
        art.setImageURI(track.albumArtUri());
        if (art.getDrawable() == null) art.setImageResource(android.R.drawable.ic_media_play);
        row.addView(art, new LinearLayout.LayoutParams(dp(48), dp(48)));

        if (number > 0) {
            TextView n = text(String.valueOf(number), 12, R.color.auren_primary);
            n.setGravity(Gravity.CENTER);
            row.addView(n, new LinearLayout.LayoutParams(dp(28), dp(48)));
        }

        LinearLayout info = column();
        TextView title = text(safeTitle(track), 14, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView artist = text(safeArtist(track), 12, R.color.text_secondary);
        artist.setMaxLines(1);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);
        info.addView(artist, margins(0, 2, 0, 0));
        row.addView(info, new LinearLayout.LayoutParams(0, dp(56), 1));

        if (number > 0) {
            TextView plays = text(playCounts.getOrDefault(track.id, 0) + " plays", 10, R.color.text_secondary);
            plays.setGravity(Gravity.CENTER);
            row.addView(plays, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }

        ImageButton overflow = iconButton(android.R.drawable.ic_menu_more, "Mais opções");
        overflow.setPadding(dp(8), dp(8), dp(8), dp(8));
        overflow.setOnClickListener(v -> showTrackMenu(v, track));
        row.addView(overflow, new LinearLayout.LayoutParams(dp(46), dp(48)));

        row.setOnClickListener(v -> { play(track); openNowPlaying(); });
        return row;
    }
'''
text, changed = replace_method(text, 'trackRow', track_method)
if not changed:
    raise SystemExit('Could not update trackRow')

# Persisted playlist helpers and the song overflow menu.
helpers = '''    private void showTrackMenu(View anchor, Track track) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Reproduzir");
        popup.getMenu().add("Reproduzir a seguir");
        popup.getMenu().add("Adicionar à fila");
        popup.getMenu().add("Adicionar à playlist");
        popup.getMenu().add(isFavorite(track) ? "Remover dos favoritos" : "Adicionar aos favoritos");
        popup.getMenu().add("Enviar");
        popup.getMenu().add("Detalhes");
        popup.setOnMenuItemClickListener(item -> {
            String action = item.getTitle().toString();
            if (action.equals("Reproduzir")) {
                play(track);
                openNowPlaying();
            } else if (action.equals("Reproduzir a seguir")) {
                addTrackToQueue(track, true);
                Toast.makeText(this, "Adicionado para reproduzir a seguir.", Toast.LENGTH_SHORT).show();
            } else if (action.equals("Adicionar à fila")) {
                addTrackToQueue(track, false);
                Toast.makeText(this, "Adicionado à fila.", Toast.LENGTH_SHORT).show();
            } else if (action.equals("Adicionar à playlist")) {
                showAddToPlaylistDialog(track);
            } else if (action.contains("favoritos")) {
                setFavorite(track, !isFavorite(track));
                showHome();
            } else if (action.equals("Enviar")) {
                shareTrack(track);
            } else if (action.equals("Detalhes")) {
                showTrackDetails(track);
            }
            return true;
        });
        popup.show();
    }

    private void addTrackToQueue(Track track, boolean next) {
        if (player == null || track == null) return;
        MediaItem item = MediaItem.fromUri(ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id));
        int count = player.getMediaItemCount();
        if (count == 0) {
            player.setMediaItem(item);
            player.prepare();
        } else if (next) {
            int index = Math.min(1, count);
            player.addMediaItem(index, item);
        } else {
            player.addMediaItem(item);
        }
    }

    private void showCreatePlaylistDialog() {
        EditText input = new EditText(this);
        input.setHint("Nome da playlist");
        input.setSingleLine(true);
        input.setPadding(dp(18), dp(12), dp(18), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Nova playlist")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Criar", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Digite um nome");
                return;
            }
            savePlaylist(name);
            dialog.dismiss();
            showPlaylists();
            Toast.makeText(this, "Playlist criada.", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private void savePlaylist(String name) {
        android.content.SharedPreferences prefs = getSharedPreferences("auren_player", MODE_PRIVATE);
        Set<String> names = new HashSet<>(prefs.getStringSet("playlist_names", new HashSet<>()));
        names.add(name);
        prefs.edit().putStringSet("playlist_names", names)
                .putString("playlist_" + name, "")
                .apply();
    }

    private int playlistTrackCount(String name) {
        String value = getSharedPreferences("auren_player", MODE_PRIVATE)
                .getString("playlist_" + name, "");
        if (value == null || value.trim().isEmpty()) return 0;
        return value.split(",").length;
    }

    private void showAddToPlaylistDialog(Track track) {
        Set<String> names = getSharedPreferences("auren_player", MODE_PRIVATE)
                .getStringSet("playlist_names", new HashSet<>());
        if (names.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Nenhuma playlist")
                    .setMessage("Crie uma playlist primeiro para adicionar esta música.")
                    .setNegativeButton("Fechar", null)
                    .setPositiveButton("Criar playlist", (d, w) -> showCreatePlaylistDialog())
                    .show();
            return;
        }
        String[] choices = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Adicionar à playlist")
                .setItems(choices, (d, which) -> addTrackToPlaylist(track, choices[which]))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void addTrackToPlaylist(Track track, String name) {
        android.content.SharedPreferences prefs = getSharedPreferences("auren_player", MODE_PRIVATE);
        String current = prefs.getString("playlist_" + name, "");
        String id = String.valueOf(track.id);
        List<String> ids = new ArrayList<>();
        if (current != null && !current.trim().isEmpty()) {
            for (String value : current.split(",")) if (!value.isEmpty()) ids.add(value);
        }
        if (!ids.contains(id)) ids.add(id);
        prefs.edit().putString("playlist_" + name, android.text.TextUtils.join(",", ids)).apply();
        Toast.makeText(this, "Adicionado à playlist " + name + ".", Toast.LENGTH_SHORT).show();
        showPlaylists();
    }

    private void shareTrack(Track track) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, safeTitle(track) + " — " + safeArtist(track));
        startActivity(Intent.createChooser(intent, "Enviar música"));
    }

    private void showTrackDetails(Track track) {
        String details = "Título: " + safeTitle(track)
                + "\\nArtista: " + safeArtist(track)
                + "\\nID: " + track.id;
        new AlertDialog.Builder(this)
                .setTitle("Detalhes da música")
                .setMessage(details)
                .setPositiveButton("Fechar", null)
                .show();
    }

'''
if 'private void showTrackMenu(' not in text:
    marker = '    private void showMostPlayed() {'
    insertion = text.find(marker)
    if insertion < 0:
        raise SystemExit('Could not find helper insertion point')
    text = text[:insertion] + helpers + text[insertion:]

# The mini player stays compact and clickable, with no unfinished options.
mini_method = '''    private LinearLayout buildMiniPlayer() {
        LinearLayout mini = column();
        mini.setBackgroundColor(Color.WHITE);
        mini.setElevation(dp(10));

        View progress = new View(this);
        progress.setBackgroundColor(getColor(R.color.auren_primary));
        mini.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));

        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(6), dp(8), dp(6));

        miniArt = artwork(52);
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(miniArt, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout info = column();
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(12), 0, dp(8), 0);

        miniTitle = text("Nothing playing", 14, R.color.text_primary);
        miniTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);

        miniArtist = text("Choose a song to start", 12, R.color.text_secondary);
        miniArtist.setSingleLine(true);
        miniArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);

        info.addView(miniTitle);
        info.addView(miniArtist, margins(0, 2, 0, 0));
        row.addView(info, new LinearLayout.LayoutParams(0, dp(52), 1));

        ImageButton next = iconButton(android.R.drawable.ic_media_next, "Next song");
        next.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        DrawableCompat.setTint(next.getDrawable(), getColor(R.color.text_primary));
        next.setOnClickListener(v -> nextTrackInPlayer());
        row.addView(next, new LinearLayout.LayoutParams(dp(42), dp(52)));

        miniPlay = iconButton(android.R.drawable.ic_media_play, "Play or pause");
        miniPlay.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.auren_primary)));
        DrawableCompat.setTint(miniPlay.getDrawable(), Color.WHITE);
        miniPlay.setPadding(dp(12), dp(12), dp(12), dp(12));
        miniPlay.setOnClickListener(v -> togglePlayback());
        row.addView(miniPlay, new LinearLayout.LayoutParams(dp(52), dp(52)));

        mini.addView(row);
        mini.setOnClickListener(v -> openNowPlaying());
        miniArt.setOnClickListener(v -> openNowPlaying());
        miniTitle.setOnClickListener(v -> openNowPlaying());
        miniArtist.setOnClickListener(v -> openNowPlaying());
        return mini;
    }
'''
text, changed = replace_method(text, 'buildMiniPlayer', mini_method)
if not changed:
    raise SystemExit('Could not update buildMiniPlayer')

if 'More player options coming soon.' in text:
    raise SystemExit('Unfinished player options message remains')
for forbidden in ('showNowPlaying();', 'roundDrawable('):
    if forbidden in text:
        raise SystemExit(f'Unresolved UI enhancement reference: {forbidden}')

path.write_text(text)
print('Auren UI enhancement completed: functional three-dot song menus and persistent playlists.')
