from pathlib import Path

path = Path('app/src/main/java/com/auren/musicplayer/MainActivity.java')
text = path.read_text()

# Tapping a track should start playback but keep the user on the current page.
text = text.replace(
    'card.setOnClickListener(v -> { play(track); openNowPlaying(); });',
    'card.setOnClickListener(v -> play(track));'
)
text = text.replace(
    'row.setOnClickListener(v -> { play(track); openNowPlaying(); });',
    'row.setOnClickListener(v -> play(track));'
)

# Give every visible track row a stable tag so the active track can be highlighted.
needle = '        row.setGravity(Gravity.CENTER_VERTICAL);\n        row.setPadding(dp(7), dp(7), dp(4), dp(7));'
replacement = '        row.setGravity(Gravity.CENTER_VERTICAL);\n        row.setPadding(dp(7), dp(7), dp(4), dp(7));\n        row.setTag(track.id);'
text = text.replace(needle, replacement, 1)

# Highlight the currently playing row without rebuilding the current page.
if 'private void highlightPlayingTrack()' not in text:
    marker = '    private void updateMiniPlayer() {'
    helper = '''    private void highlightPlayingTrack() {
        if (pageContainer == null) return;
        updatePlayingViews(pageContainer);
    }

    private void updatePlayingViews(View view) {
        Object tag = view.getTag();
        if (tag instanceof Long) {
            boolean playing = currentTrack != null && ((Long) tag) == currentTrack.id;
            view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getColor(playing ? R.color.playing_background : R.color.card)));
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView tv = (TextView) child;
                        tv.setTextColor(getColor(playing ? R.color.playing_text : R.color.text_primary));
                    }
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                updatePlayingViews(group.getChildAt(i));
            }
        }
    }

'''
    if marker not in text:
        raise SystemExit('updateMiniPlayer marker not found')
    text = text.replace(marker, helper + marker, 1)

# Refresh the visible state whenever playback changes to another track.
old = '''        player.play();
        updateMiniPlayer();
    }'''
new = '''        player.play();
        updateMiniPlayer();
        highlightPlayingTrack();
    }'''
if old in text:
    text = text.replace(old, new, 1)

# Avoid accidental auto-opening from ordinary track cards.
if 'card.setOnClickListener(v -> { play(track); openNowPlaying(); });' in text:
    raise SystemExit('Track card still opens Now Playing automatically')
if 'row.setOnClickListener(v -> { play(track); openNowPlaying(); });' in text:
    raise SystemExit('Track row still opens Now Playing automatically')

path.write_text(text)
print('Player behavior fixed: track taps stay on the current page and active tracks are highlighted.')
