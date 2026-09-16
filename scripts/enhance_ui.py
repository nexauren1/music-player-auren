from pathlib import Path

path = Path("app/src/main/java/com/auren/musicplayer/MainActivity.java")
text = path.read_text()

# Keep the generated source self-contained when the enhancement script runs.
imports = {
    "import android.app.AlertDialog;": "import android.app.Dialog;\n",
    "import android.widget.EditText;": "import android.widget.ImageButton;\n",
    "import android.widget.PopupMenu;": "import android.widget.ImageButton;\n",
    "import android.widget.HorizontalScrollView;": "import android.widget.ImageView;\n",
}
for required, anchor in imports.items():
    if required not in text:
        text = text.replace(anchor, required + "\n" + anchor, 1)

# The hamburger belongs to the top app bar, not inside only the Home page.
home_menu = '''        ImageButton menu = iconButton(android.R.drawable.ic_menu_sort_by_size, "Open menu");
        menu.setOnClickListener(v -> showAppMenu(menu));
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
'''
text = text.replace(home_menu, "", 1)

# Make the top bar persistent so the menu is available on every library page.
old_shell = '''    private void buildShell() {
        LinearLayout root = column();
        root.setBackgroundColor(getColor(R.color.surface));

        pageContainer = column();
        root.addView(pageContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        miniContainer = buildMiniPlayer();
        root.addView(miniContainer, margins(12, 4, 12, 4));
        root.addView(buildBottomNavigation());
        setContentView(root);
        showHome();
    }
'''
new_shell = '''    private void buildShell() {
        LinearLayout root = column();
        root.setBackgroundColor(getColor(R.color.surface));

        root.addView(buildTopBar());

        pageContainer = column();
        root.addView(pageContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        miniContainer = buildMiniPlayer();
        root.addView(miniContainer, margins(12, 4, 12, 4));
        root.addView(buildBottomNavigation());
        setContentView(root);
        showHome();
    }

    private View buildTopBar() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(6), dp(10), dp(6));
        bar.setBackgroundColor(getColor(R.color.auren_primary));
        bar.setElevation(dp(4));

        ImageButton menu = iconButton(android.R.drawable.ic_menu_sort_by_size, "Open menu");
        menu.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.auren_primary)));
        DrawableCompat.setTint(menu.getDrawable(), Color.WHITE);
        menu.setOnClickListener(v -> showAppMenu(menu));
        bar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("Auren Music", 19, android.R.color.white);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        ImageButton search = iconButton(android.R.drawable.ic_menu_search, "Search music");
        search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.auren_primary)));
        DrawableCompat.setTint(search.getDrawable(), Color.WHITE);
        search.setOnClickListener(v -> showSearchDialog());
        bar.addView(search, new LinearLayout.LayoutParams(dp(48), dp(48)));

        return bar;
    }
'''
if old_shell not in text:
    raise SystemExit("buildShell block not found")
text = text.replace(old_shell, new_shell, 1)

# The Library screen is a hub, like the requested player layout: sections first,
# then the actual list screens when a section is opened.
start = text.find("    private void showLibrary(boolean favoritesOnly) {")
end = text.find("    private void showPlaylists() {", start)
if start == -1 or end == -1:
    raise SystemExit("showLibrary block not found")

library_block = '''    private void showLibrary() {
        setActiveTab(libraryTab);
        pageContainer.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(22));

        TextView eyebrow = text("YOUR LIBRARY", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Biblioteca", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);
        content.addView(title, margins(0, 3, 0, 16));

        content.addView(librarySectionCard("Mais tocadas", "As músicas que você mais ouve", "♫", v -> showMostPlayed()));
        content.addView(librarySectionCard("Recentes", "O que você ouviu recentemente", "◷", v -> showRecent()), margins(0, 10, 0, 0));
        content.addView(librarySectionCard("Playlists", "Suas coleções de músicas", "▤", v -> showPlaylists()), margins(0, 10, 0, 0));
        content.addView(librarySectionCard("Sugestões", "Músicas escolhidas da sua biblioteca", "✦", v -> showSuggestions()), margins(0, 10, 0, 0));
        content.addView(librarySectionCard("Favoritos", "Músicas que você marcou com ♥", "♥", v -> showLibrary(true)), margins(0, 10, 0, 0));

        scroll.addView(content);
        pageContainer.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private void showLibrary(boolean favoritesOnly) {
        setActiveTab(libraryTab);
        pageContainer.removeAllViews();

        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(18));
        LinearLayout header = row();
        LinearLayout titles = column();
        TextView eyebrow = text("YOUR FAVORITES", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Favoritos", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titles.addView(eyebrow);
        titles.addView(title, margins(0, 3, 0, 0));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton search = iconButton(android.R.drawable.ic_menu_search, "Search music");
        search.setOnClickListener(v -> showSearchDialog());
        header.addView(search, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = column();
        List<Track> source = new ArrayList<>();
        for (Track t : tracks) if (isFavorite(t)) source.add(t);
        if (source.isEmpty()) {
            list.addView(emptyCard("Ainda não há favoritos. Toque no coração durante a reprodução."));
        } else {
            for (Track t : source) list.addView(trackRow(t, 0));
        }
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        pageContainer.addView(content);
    }

    private View librarySectionCard(String title, String subtitle, String icon, View.OnClickListener listener) {
        LinearLayout card = rounded(Color.WHITE, 20);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(12), dp(12));
        card.setElevation(dp(2));

        TextView iconView = text(icon, 25, R.color.auren_primary);
        iconView.setGravity(Gravity.CENTER);
        iconView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(iconView, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout info = column();
        TextView name = text(title, 16, R.color.text_primary);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(name);
        info.addView(text(subtitle, 12, R.color.text_secondary), margins(0, 3, 0, 0));
        card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = text("›", 28, R.color.text_secondary);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(48)));
        card.setOnClickListener(listener);
        return card;
    }

    private void showRecent() {
        setActiveTab(libraryTab);
        pageContainer.removeAllViews();
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(18));
        TextView eyebrow = text("YOUR HISTORY", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Recentes", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);
        content.addView(title, margins(0, 3, 0, 14));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = column();
        if (recentTracks.isEmpty()) {
            list.addView(emptyCard("As músicas reproduzidas aparecerão aqui."));
        } else {
            for (Track t : recentTracks) list.addView(trackRow(t, 0));
        }
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        pageContainer.addView(content);
    }

    private void showSuggestions() {
        setActiveTab(libraryTab);
        pageContainer.removeAllViews();
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(18));
        TextView eyebrow = text("FOR YOU", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Sugestões", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);
        content.addView(title, margins(0, 3, 0, 14));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = column();
        List<Track> suggestions = suggestionTracks();
        if (suggestions.isEmpty()) {
            list.addView(emptyCard("As sugestões aparecerão quando sua biblioteca for carregada."));
        } else {
            for (Track t : suggestions) list.addView(trackRow(t, 0));
        }
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        pageContainer.addView(content);
    }

'''
text = text[:start] + library_block + text[end:]

# Route the existing Library entry points to the new Library hub.
text = text.replace('showLibrary(false)', 'showLibrary()')

# Keep the menu useful in Portuguese and include the requested Library hub.
old_menu = '''        menu.getMenu().add("Home");
        menu.getMenu().add("Library");
        menu.getMenu().add("Favorites");
        menu.getMenu().add("Playlists");
        menu.getMenu().add("Most played");
        menu.getMenu().add("Settings");
        menu.getMenu().add("About Auren");
'''
new_menu = '''        menu.getMenu().add("Início");
        menu.getMenu().add("Biblioteca");
        menu.getMenu().add("Favoritos");
        menu.getMenu().add("Playlists");
        menu.getMenu().add("Mais tocadas");
        menu.getMenu().add("Configurações");
        menu.getMenu().add("Sobre Auren");
'''
text = text.replace(old_menu, new_menu, 1)
old_routes = '''            if (title.equals("Home")) showHome();
            else if (title.equals("Library")) showLibrary();
            else if (title.equals("Favorites")) showLibrary(true);
            else if (title.equals("Playlists")) showPlaylists();
            else if (title.equals("Most played")) showMostPlayed();
            else if (title.equals("Settings")) startActivity(new Intent(this, SettingsActivity.class));
            else if (title.equals("About Auren")) showAboutDialog();
'''
new_routes = '''            if (title.equals("Início")) showHome();
            else if (title.equals("Biblioteca")) showLibrary();
            else if (title.equals("Favoritos")) showLibrary(true);
            else if (title.equals("Playlists")) showPlaylists();
            else if (title.equals("Mais tocadas")) showMostPlayed();
            else if (title.equals("Configurações")) startActivity(new Intent(this, SettingsActivity.class));
            else if (title.equals("Sobre Auren")) showAboutDialog();
'''
text = text.replace(old_routes, new_routes, 1)

path.write_text(text)
print("Top menu and Library hub enhancement completed.")
