from pathlib import Path

path = Path('app/src/main/java/com/auren/musicplayer/MainActivity.java')
text = path.read_text()

# The playlist enhancement script can skip its helper block when an older
# customPlaylistCard already exists. Keep the build self-healing.
if 'private boolean isCustomPlaylist(String name)' not in text:
    method = '''    private boolean isCustomPlaylist(String name) {
        return getSharedPreferences("auren_player", MODE_PRIVATE)
                .getStringSet("playlist_names", new HashSet<>())
                .contains(name);
    }

'''
    marker = '    private View playlistCard('
    pos = text.find(marker)
    if pos < 0:
        raise SystemExit('playlistCard marker not found')
    text = text[:pos] + method + text[pos:]

path.write_text(text)
print('Playlist build helper verified.')
