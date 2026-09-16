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
]
for imp in required_imports:
    if imp not in text:
        anchor = "import android.app.AlertDialog;"
        text = text.replace(anchor, imp + "\n" + anchor, 1)

# Repair references left by earlier UI enhancement revisions.
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


# Add the large artwork-led Home hero once.
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
        card.setOnClickListener(v -> {
            if (currentTrack != null) openNowPlaying();
        });
        return card;
    }
'''
if 'private View buildHomeHero()' not in text:
    insertion = text.find('    private void showHome()')
    if insertion >= 0:
        text = text[:insertion] + hero_method + '\n' + text[insertion:]

# Put every local music track directly on Home, below the discovery sections.
all_songs_block = '''        // AUREN_ALL_SONGS_HOME_START
        addSectionHeader(content, "Todas as músicas", tracks.size() + " músicas", v -> showLibrary());
        if (tracks.isEmpty()) {
            content.addView(emptyCard("Nenhuma música encontrada no dispositivo."));
        } else {
            for (Track track : tracks) {
                content.addView(trackRow(track, 0));
            }
        }
        // AUREN_ALL_SONGS_HOME_END

'''
if 'AUREN_ALL_SONGS_HOME_START' not in text:
    marker = '        addSectionHeader(content, "Suggestions for you", "Refresh", v -> showHome());\n'
    text = text.replace(marker, all_songs_block + marker, 1)

# Replace the mini player with a cleaner reference-style bottom player.
# It deliberately has no placeholder action: every visible control does something useful.
mini_method = '''    private LinearLayout buildMiniPlayer() {
        LinearLayout mini = column();
        mini.setBackgroundColor(Color.WHITE);
        mini.setElevation(dp(10));
        mini.setPadding(0, 0, 0, 0);

        View progressAccent = new View(this);
        progressAccent.setBackgroundColor(getColor(R.color.auren_primary));
        mini.addView(progressAccent, new LinearLayout.LayoutParams(-1, dp(2)));

        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(6), dp(8), dp(6));
        row.setMinimumHeight(dp(68));

        miniArt = artwork(56);
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniArt.setClipToOutline(true);
        row.addView(miniArt, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout info = column();
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(12), 0, dp(8), 0);
        miniTitle = text("Nothing playing", 14, R.color.text_primary);
        miniTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        miniTitle.setMaxLines(1);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniArtist = text("Choose a song to start", 12, R.color.text_secondary);
        miniArtist.setMaxLines(1);
        miniArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(miniTitle);
        info.addView(miniArtist, margins(0, 3, 0, 0));
        row.addView(info, new LinearLayout.LayoutParams(0, dp(56), 1));

        ImageButton miniPlayButton = iconButton(android.R.drawable.ic_media_play, "Play or pause");
        miniPlay = miniPlayButton;
        miniPlayButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(getColor(R.color.auren_primary))
        );
        DrawableCompat.setTint(miniPlayButton.getDrawable(), Color.WHITE);
        miniPlayButton.setPadding(dp(13), dp(13), dp(13), dp(13));
        miniPlayButton.setOnClickListener(v -> togglePlayback());
        row.addView(miniPlayButton, new LinearLayout.LayoutParams(dp(52), dp(52)));

        ImageButton next = iconButton(android.R.drawable.ic_media_next, "Next song");
        next.setOnClickListener(v -> nextTrackInPlayer());
        row.addView(next, new LinearLayout.LayoutParams(dp(44), dp(52)));

        mini.addView(row);
        mini.setOnClickListener(v -> openNowPlaying());
        miniArt.setOnClickListener(v -> openNowPlaying());
        miniTitle.setOnClickListener(v -> openNowPlaying());
        miniArtist.setOnClickListener(v -> openNowPlaying());
        return mini;
    }
'''
updated, changed = replace_method(text, 'buildMiniPlayer', mini_method)
if changed:
    text = updated

# Replace the top-right Now Playing placeholder with real actions.
old_more = '''        ImageButton more = iconButton(android.R.drawable.ic_menu_more, "More options");
        more.setOnClickListener(v -> Toast.makeText(this, "More player options coming soon.", Toast.LENGTH_SHORT).show());
        top.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
'''
new_more = '''        ImageButton more = iconButton(android.R.drawable.ic_menu_more, "Player options");
        more.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, more);
            popup.getMenu().add("Reproduzir novamente");
            popup.getMenu().add(isFavorite(currentTrack) ? "Remover dos favoritos" : "Adicionar aos favoritos");
            popup.getMenu().add("Aleatório");
            popup.getMenu().add("Fechar reprodução");
            popup.setOnMenuItemClickListener(item -> {
                String action = item.getTitle().toString();
                if (action.equals("Reproduzir novamente")) {
                    player.seekTo(0);
                    player.play();
                    refreshNowPlaying();
                } else if (action.contains("favoritos")) {
                    setFavorite(currentTrack, !isFavorite(currentTrack));
                    updateMiniPlayer();
                    refreshNowPlaying();
                } else if (action.equals("Aleatório")) {
                    shufflePlay();
                    refreshNowPlaying();
                } else {
                    closeNowPlaying();
                }
                return true;
            });
            popup.show();
        });
        top.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
'''
if old_more in text:
    text = text.replace(old_more, new_more, 1)

# Keep the mini player flush with the app edges like a real docked player.
text = text.replace(
    'root.addView(miniContainer, margins(12, 4, 12, 4));',
    'root.addView(miniContainer, new LinearLayout.LayoutParams(-1, dp(70)));',
    1,
)

# Never ship the old placeholder message again.
text = text.replace("More player options coming soon.", "")

for forbidden in ("showNowPlaying();", "roundDrawable(", "More player options coming soon."):
    if forbidden in text:
        raise SystemExit(f"Unresolved UI enhancement reference: {forbidden}")

path.write_text(text)
print("Auren UI enhancement completed: cleaner mini player, real player actions, and all songs on Home.")
