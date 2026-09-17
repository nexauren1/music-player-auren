from pathlib import Path
import re

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

# The UI enhancement script can encounter an already-created mini-player
# functions button. Keep only one declaration so repeated builds remain safe.
functions_block = '''        ImageButton functions = iconButton(android.R.drawable.ic_menu_more, "Funções e efeitos");
        functions.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        DrawableCompat.setTint(functions.getDrawable(), getColor(R.color.text_primary));
        functions.setOnClickListener(v -> showMiniPlayerMenu(functions));
        row.addView(functions, new LinearLayout.LayoutParams(dp(38), dp(52)));'''
while text.count('ImageButton functions = iconButton(android.R.drawable.ic_menu_more, "Funções e efeitos");') > 1:
    text = text.replace(functions_block, '', 1)

# Keep the full Now Playing screen synchronized with Previous/Next. Rebuild its
# content in place so title, artist, artwork and controls all belong to the
# newly selected track.
def replace_method(source, name, replacement):
    match = re.search(
        r'(?m)^\s*private\s+[\w<>]+\s+' + re.escape(name) + r'\s*\([^)]*\)\s*\{',
        source
    )
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

refresh_replacement = '''    private void refreshNowPlaying() {
        if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) {
            nowPlayingDialog.setContentView(buildNowPlayingView());
            Window window = nowPlayingDialog.getWindow();
            if (window != null) {
                window.setLayout(-1, -1);
            }
        } else if (currentTrack != null) {
            openNowPlaying();
        }
    }'''
text, replaced = replace_method(text, 'refreshNowPlaying', refresh_replacement)
if not replaced:
    raise SystemExit('refreshNowPlaying method not found')

# Avoid accidental auto-opening from ordinary track cards.
if 'card.setOnClickListener(v -> { play(track); openNowPlaying(); });' in text:
    raise SystemExit('Track card still opens Now Playing automatically')
if 'row.setOnClickListener(v -> { play(track); openNowPlaying(); });' in text:
    raise SystemExit('Track row still opens Now Playing automatically')

# Fail early if the mini-player still contains duplicate function declarations.
if text.count('ImageButton functions = iconButton(android.R.drawable.ic_menu_more, "Funções e efeitos");') > 1:
    raise SystemExit('Duplicate mini-player functions declaration remains')

path.write_text(text)
print('Player behavior fixed: track taps stay on the current page, active tracks are highlighted, and Now Playing stays synchronized.')
