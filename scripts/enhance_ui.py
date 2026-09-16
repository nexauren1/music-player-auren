from pathlib import Path
import re

path = Path("app/src/main/java/com/auren/musicplayer/MainActivity.java")
text = path.read_text()

required_imports = [
    "import android.app.Dialog;",
    "import android.widget.ImageButton;",
    "import android.widget.ImageView;",
    "import android.widget.HorizontalScrollView;",
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

# Replace the mini player with a layout closer to the reference: full-width,
# compact artwork, title/artist, prominent blue play button, and progress accent.
mini_method = '''    private LinearLayout buildMiniPlayer() {
        LinearLayout mini = column();
        mini.setBackgroundColor(Color.WHITE);
        mini.setElevation(dp(8));
        mini.setPadding(0, 0, 0, 0);

        View progressAccent = new View(this);
        progressAccent.setBackgroundColor(getColor(R.color.auren_primary));
        mini.addView(progressAccent, new LinearLayout.LayoutParams(-1, dp(3)));

        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(7), dp(8), dp(7));

        miniArt = artwork(54);
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(miniArt, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout info = column();
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(12), 0, dp(6), 0);
        miniTitle = text("Nothing playing", 14, R.color.text_primary);
        miniTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        miniTitle.setMaxLines(1);
        miniArtist = text("Choose a song to start", 12, R.color.text_secondary);
        miniArtist.setMaxLines(1);
        info.addView(miniTitle);
        info.addView(miniArtist, margins(0, 2, 0, 0));
        row.addView(info, new LinearLayout.LayoutParams(0, dp(54), 1));

        ImageButton miniMore = iconButton(android.R.drawable.ic_menu_more, "Player options");
        miniMore.setOnClickListener(v -> openNowPlaying());
        row.addView(miniMore, new LinearLayout.LayoutParams(dp(42), dp(54)));

        miniPlay = iconButton(android.R.drawable.ic_media_play, "Play or pause");
        miniPlay.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.auren_primary)));
        DrawableCompat.setTint(miniPlay.getDrawable(), Color.WHITE);
        miniPlay.setPadding(dp(12), dp(12), dp(12), dp(12));
        miniPlay.setOnClickListener(v -> togglePlayback());
        row.addView(miniPlay, new LinearLayout.LayoutParams(dp(54), dp(54)));

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

# Do not allow the release pipeline to proceed with references that are
# known not to exist in the current MainActivity API.
for forbidden in ("showNowPlaying();", "roundDrawable("):
    if forbidden in text:
        raise SystemExit(f"Unresolved UI enhancement reference: {forbidden}")

path.write_text(text)
print("Auren UI enhancement completed: reference-style mini player + all songs on Home.")
