from pathlib import Path

path = Path("app/src/main/java/com/auren/musicplayer/MainActivity.java")
text = path.read_text()

if "import android.app.AlertDialog;" not in text:
    text = text.replace("import android.app.Dialog;\n", "import android.app.AlertDialog;\nimport android.app.Dialog;\n", 1)
if "import android.widget.PopupMenu;" not in text:
    text = text.replace("import android.widget.ImageButton;\n", "import android.widget.EditText;\nimport android.widget.ImageButton;\nimport android.widget.PopupMenu;\n", 1)

old = '''ImageButton settings = iconButton(android.R.drawable.ic_menu_preferences, "Settings");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));'''
new = '''ImageButton menu = iconButton(android.R.drawable.ic_menu_sort_by_size, "Open menu");
        menu.setOnClickListener(v -> showAppMenu(menu));
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));'''
text = text.replace(old, new, 1)

old = '''ImageButton search = iconButton(android.R.drawable.ic_menu_search, "Search");
        search.setOnClickListener(v -> Toast.makeText(this, "Search is ready for the next library update.", Toast.LENGTH_SHORT).show());'''
new = '''ImageButton search = iconButton(android.R.drawable.ic_menu_search, "Search music");
        search.setOnClickListener(v -> showSearchDialog());'''
text = text.replace(old, new, 1)

marker = "    private LinearLayout buildMiniPlayer() {"
if "private void showAppMenu(" not in text:
    helpers = '''    private void showAppMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Home");
        menu.getMenu().add("Library");
        menu.getMenu().add("Favorites");
        menu.getMenu().add("Playlists");
        menu.getMenu().add("Most played");
        menu.getMenu().add("Settings");
        menu.getMenu().add("About Auren");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Home")) showHome();
            else if (title.equals("Library")) showLibrary(false);
            else if (title.equals("Favorites")) showLibrary(true);
            else if (title.equals("Playlists")) showPlaylists();
            else if (title.equals("Most played")) showMostPlayed();
            else if (title.equals("Settings")) startActivity(new Intent(this, SettingsActivity.class));
            else if (title.equals("About Auren")) showAboutDialog();
            return true;
        });
        menu.show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Auren Music")
                .setMessage("A clean, modern music player built around your local library.\\n\\nVersion " + BuildConfig.VERSION_NAME + "\\n\\nMusic that moves with you.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setHint("Song or artist");
        input.setSingleLine(true);
        int pad = dp(18);
        input.setPadding(pad, pad, pad, pad);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Search your music")
                .setView(input)
                .setNegativeButton("Close", null)
                .create();
        input.setOnEditorActionListener((v, actionId, event) -> {
            performSearch(input.getText().toString(), dialog);
            return true;
        });
        dialog.setOnShowListener(d -> input.requestFocus());
        dialog.show();
    }

    private void performSearch(String query, AlertDialog dialog) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (q.isEmpty()) return;
        List<Track> matches = new ArrayList<>();
        for (Track track : tracks) {
            String title = safeTitle(track).toLowerCase(Locale.US);
            String artist = safeArtist(track).toLowerCase(Locale.US);
            if (title.contains(q) || artist.contains(q)) matches.add(track);
        }
        dialog.dismiss();
        if (matches.isEmpty()) {
            Toast.makeText(this, "No songs found.", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout list = column();
        list.setPadding(dp(18), dp(10), dp(18), dp(18));
        TextView resultTitle = text(matches.size() + " result" + (matches.size() == 1 ? "" : "s"), 13, R.color.auren_primary);
        resultTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        list.addView(resultTitle, margins(0, 4, 0, 8));
        for (Track track : matches) list.addView(trackRow(track, 0));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        new AlertDialog.Builder(this)
                .setTitle("Search results")
                .setView(scroll)
                .setPositiveButton("Done", null)
                .show();
    }

'''
    text = text.replace(marker, helpers + marker, 1)

path.write_text(text)
print("UI enhancement script completed.")
