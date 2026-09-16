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
        info.addView(title, margins(0, 4, 0, 2));

        String heroArtist = miniArtist == null ? "Descubra, ouça e aproveite" : miniArtist.getText().toString();
        if (heroArtist.trim().isEmpty()) heroArtist = "Descubra, ouça e aproveite";
        info.addView(text(heroArtist, 12, android.R.color.white));

        TextView action = text("Abrir reprodução  ›", 12, android.R.color.white);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(action, margins(0, 12, 0, 0));
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

# Use a real left-side navigation drawer instead of a small popup.
drawer_method = '''    private void showAppMenu(View anchor) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = column();
        root.setBackgroundColor(getColor(R.color.surface));

        LinearLayout header = column();
        header.setPadding(dp(20), dp(28), dp(20), dp(20));
        header.setBackgroundColor(getColor(R.color.auren_primary));

        TextView brand = text("AUREN", 25, android.R.color.white);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(brand);
        TextView subtitle = text("Music Player", 13, android.R.color.white);
        header.addView(subtitle, margins(0, 2, 0, 0));
        TextView version = text("Versão " + BuildConfig.VERSION_NAME, 11, android.R.color.white);
        header.addView(version, margins(0, 12, 0, 0));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout items = column();
        items.setPadding(dp(10), dp(12), dp(10), dp(16));

        addDrawerItem(items, "⌂", "Início", () -> { dialog.dismiss(); showHome(); });
        addDrawerItem(items, "♫", "Biblioteca", () -> { dialog.dismiss(); showLibrary(); });
        addDrawerItem(items, "♥", "Favoritos", () -> { dialog.dismiss(); showLibrary(true); });
        addDrawerItem(items, "▤", "Playlists", () -> { dialog.dismiss(); showPlaylists(); });
        addDrawerItem(items, "🔥", "Mais tocadas", () -> { dialog.dismiss(); showMostPlayed(); });
        addDrawerItem(items, "◷", "Recentes", () -> { dialog.dismiss(); showRecent(); });
        addDrawerItem(items, "✦", "Sugestões", () -> { dialog.dismiss(); showSuggestions(); });

        View divider = new View(this);
        divider.setBackgroundColor(0xFFE4E8EF);
        items.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        addDrawerItem(items, "⚙", "Configurações", () -> {
            dialog.dismiss();
            startActivity(new Intent(this, SettingsActivity.class));
        });
        addDrawerItem(items, "ⓘ", "Sobre Auren", () -> { dialog.dismiss(); showAboutDialog(); });

        scroll.addView(items);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.28f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setGravity(Gravity.START | Gravity.TOP);
            window.setLayout(
                    Math.min(dp(330), getResources().getDisplayMetrics().widthPixels - dp(24)),
                    -1
            );
        }
    }

    private void addDrawerItem(LinearLayout parent, String icon, String label, Runnable action) {
        LinearLayout item = rounded(Color.WHITE, 16);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(14), dp(8), dp(12), dp(8));

        TextView iconView = text(icon, 22, R.color.text_secondary);
        iconView.setGravity(Gravity.CENTER);
        item.addView(iconView, new LinearLayout.LayoutParams(dp(48), dp(50)));

        TextView name = text(label, 15, R.color.text_primary);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        item.addView(name, new LinearLayout.LayoutParams(0, dp(50), 1));

        TextView arrow = text("›", 25, R.color.text_secondary);
        item.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(50)));
        item.setOnClickListener(v -> action.run());
        parent.addView(item, margins(0, 3, 0, 3));
    }
'''
if 'private void addDrawerItem' not in text:
    updated, changed = replace_method(text, 'showAppMenu', drawer_method)
    if changed:
        text = updated

# Do not allow the release pipeline to proceed with references that are
# known not to exist in the current MainActivity API.
for forbidden in ("showNowPlaying();", "roundDrawable("):
    if forbidden in text:
        raise SystemExit(f"Unresolved UI enhancement reference: {forbidden}")

path.write_text(text)
print("Auren visual enhancement completed: artwork hero + side navigation drawer.")
