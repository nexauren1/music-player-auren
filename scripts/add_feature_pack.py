from pathlib import Path
import re

path = Path('app/src/main/java/com/auren/musicplayer/MainActivity.java')
text = path.read_text()


def add_import(import_line, anchor):
    global text
    if import_line not in text:
        if anchor not in text:
            raise SystemExit(f'Import anchor not found: {anchor}')
        text = text.replace(anchor, anchor + '\n' + import_line, 1)


def replace_method(source, name, replacement):
    pattern = r'(?m)^\s*private\s+[\w<>\[\], ?]+\s+' + re.escape(name) + r'\s*\([^)]*\)\s*\{'
    match = re.search(pattern, source)
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


add_import('import android.media.audiofx.Equalizer;', 'import android.graphics.drawable.GradientDrawable;')
add_import('import androidx.media3.common.Player;', 'import androidx.media3.common.PlaybackParameters;')

# Extra state for the advanced player features.
if 'private final List<Track> playQueue' not in text:
    marker = '    private float playbackPitch = 1.0f;'
    fields = '''    private float playbackPitch = 1.0f;
    private final List<Track> playQueue = new ArrayList<>();
    private long sleepTimerEndMs = 0L;
    private boolean sleepAtTrackEnd;
    private long abStartMs = -1L;
    private long abEndMs = -1L;
    private boolean abRepeatEnabled;
    private Equalizer equalizer;
    private String librarySortMode = "title";'''
    if marker not in text:
        raise SystemExit('playbackPitch field anchor not found')
    text = text.replace(marker, fields, 1)

# Gracefully pause when headphones are disconnected.
needle = '        player = new ExoPlayer.Builder(this).build();\n'
replacement = needle + '        player.setHandleAudioBecomingNoisy(true);\n'
if 'player.setHandleAudioBecomingNoisy(true);' not in text:
    if needle not in text:
        raise SystemExit('player initialization anchor not found')
    text = text.replace(needle, replacement, 1)

# Run the feature timer/check loop alongside the existing progress loop.
needle = '            if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) updateNowPlayingProgress();\n            handler.postDelayed(this, 500);'
replacement = '            if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) updateNowPlayingProgress();\n            checkAdvancedFeatures();\n            handler.postDelayed(this, 500);'
if 'checkAdvancedFeatures();' not in text:
    if needle not in text:
        raise SystemExit('progress updater anchor not found')
    text = text.replace(needle, replacement, 1)

# Make the mini-player menu the main entry point for the advanced tools.
mini_menu = '''    private void showMiniPlayerMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Fila de reprodução");
        popup.getMenu().add("Temporizador de sono");
        popup.getMenu().add("Repetição");
        popup.getMenu().add("Repetir trecho A-B");
        popup.getMenu().add("Equalizador");
        popup.getMenu().add("Ordenar biblioteca");
        popup.getMenu().add("Atualizar biblioteca");
        popup.getMenu().add("Efeitos");
        popup.getMenu().add("Velocidade: " + formatEffectValue(playbackSpeed));
        popup.getMenu().add("Pitch: " + formatEffectValue(playbackPitch));
        popup.getMenu().add("Detalhes da música");
        popup.getMenu().add("Repor efeitos");
        popup.setOnMenuItemClickListener(item -> {
            String action = item.getTitle().toString();
            if (action.equals("Fila de reprodução")) {
                showQueueDialog();
            } else if (action.equals("Temporizador de sono")) {
                showSleepTimerDialog();
            } else if (action.equals("Repetição")) {
                showRepeatDialog();
            } else if (action.equals("Repetir trecho A-B")) {
                showAbRepeatDialog();
            } else if (action.equals("Equalizador")) {
                showEqualizerDialog();
            } else if (action.equals("Ordenar biblioteca")) {
                showSortDialog();
            } else if (action.equals("Atualizar biblioteca")) {
                loadMusic();
                Toast.makeText(this, "Biblioteca atualizada.", Toast.LENGTH_SHORT).show();
            } else if (action.equals("Efeitos")) {
                showEffectsDialog();
            } else if (action.startsWith("Velocidade:")) {
                showSpeedDialog();
            } else if (action.startsWith("Pitch:")) {
                showPitchDialog();
            } else if (action.equals("Detalhes da música")) {
                if (currentTrack != null) showTrackDetails(currentTrack);
            } else if (action.equals("Repor efeitos")) {
                playbackSpeed = 1.0f;
                playbackPitch = 1.0f;
                applyPlaybackEffects();
                Toast.makeText(this, "Efeitos repostos.", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
    }'''
text, ok = replace_method(text, 'showMiniPlayerMenu', mini_menu)
if not ok:
    raise SystemExit('showMiniPlayerMenu not found')

# Use a real in-app queue rather than replacing the player's current item.
queue_method = '''    private void addTrackToQueue(Track track, boolean next) {
        if (track == null) return;
        playQueue.remove(track);
        if (next) playQueue.add(0, track);
        else playQueue.add(track);
    }'''
text, ok = replace_method(text, 'addTrackToQueue', queue_method)
if not ok:
    raise SystemExit('addTrackToQueue not found')

next_method = '''    private void nextTrackInPlayer() {
        if (!playQueue.isEmpty()) {
            Track next = playQueue.remove(0);
            if (sleepAtTrackEnd) {
                sleepAtTrackEnd = false;
                sleepTimerEndMs = 0L;
            }
            play(next);
            refreshNowPlaying();
            return;
        }
        if (tracks.isEmpty()) return;
        int index = currentTrack == null ? -1 : tracks.indexOf(currentTrack);
        play(tracks.get(index >= tracks.size() - 1 ? 0 : index + 1));
        refreshNowPlaying();
    }'''
text, ok = replace_method(text, 'nextTrackInPlayer', next_method)
if not ok:
    raise SystemExit('nextTrackInPlayer not found')

# Replace the placeholder queue action in Now Playing.
text = text.replace('playerAction("☰", "Queue", v -> Toast.makeText(this, tracks.size() + " songs in library", Toast.LENGTH_SHORT).show())',
                    'playerAction("☰", "Queue", v -> showQueueDialog())', 1)

# Rebuild sort order after scanning the MediaStore library.
needle = '        if (pageContainer != null) showHome();\n    }\n\n    private View actionCard'
replacement = '        applyLibrarySort();\n        if (pageContainer != null) showHome();\n    }\n\n    private View actionCard'
if 'applyLibrarySort();' not in text:
    if needle not in text:
        raise SystemExit('loadMusic ending anchor not found')
    text = text.replace(needle, replacement, 1)

# Insert the feature methods before the existing actionCard helper.
if 'private void showQueueDialog()' not in text:
    marker = '    private View actionCard(String icon, String label, View.OnClickListener listener) {'
    if marker not in text:
        raise SystemExit('feature insertion marker not found')
    methods = '''    private void showQueueDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Fila de reprodução");
        if (playQueue.isEmpty()) {
            builder.setMessage("A fila está vazia. Use ⋮ em uma música e escolha Adicionar à fila ou Reproduzir a seguir.");
        } else {
            String[] labels = new String[playQueue.size()];
            for (int i = 0; i < playQueue.size(); i++) {
                Track track = playQueue.get(i);
                labels[i] = (i + 1) + ". " + safeTitle(track) + " — " + safeArtist(track);
            }
            builder.setItems(labels, (dialog, which) -> {
                Track selected = playQueue.remove(which);
                play(selected);
                refreshNowPlaying();
            });
        }
        builder.setNeutralButton("Limpar", (d, w) -> playQueue.clear());
        builder.setNegativeButton("Fechar", null);
        builder.show();
    }

    private void showSleepTimerDialog() {
        String active = sleepTimerEndMs > 0L
                ? "Temporizador ativo: " + formatRemaining(sleepTimerEndMs - System.currentTimeMillis())
                : "Sem temporizador ativo";
        String[] options = {"Desligar", "15 minutos", "30 minutos", "45 minutos", "60 minutos", "90 minutos", "Até a faixa terminar"};
        new AlertDialog.Builder(this)
                .setTitle("Temporizador de sono")
                .setMessage(active)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        sleepTimerEndMs = 0L;
                        sleepAtTrackEnd = false;
                        Toast.makeText(this, "Temporizador desligado.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (which == options.length - 1) {
                        sleepTimerEndMs = 0L;
                        sleepAtTrackEnd = true;
                        Toast.makeText(this, "A reprodução termina ao fim da faixa.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int[] minutes = {0, 15, 30, 45, 60, 90};
                    sleepAtTrackEnd = false;
                    sleepTimerEndMs = System.currentTimeMillis() + minutes[which] * 60_000L;
                    Toast.makeText(this, "Temporizador: " + minutes[which] + " min.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private String formatRemaining(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        long minutes = seconds / 60L;
        seconds %= 60L;
        return minutes + ":" + String.format(Locale.US, "%02d", seconds);
    }

    private void checkAdvancedFeatures() {
        if (player == null) return;
        if (sleepTimerEndMs > 0L && System.currentTimeMillis() >= sleepTimerEndMs) {
            player.pause();
            sleepTimerEndMs = 0L;
            sleepAtTrackEnd = false;
            updateMiniPlayer();
            Toast.makeText(this, "Temporizador concluído.", Toast.LENGTH_SHORT).show();
        }
        if (abRepeatEnabled && abStartMs >= 0L && abEndMs > abStartMs
                && player.isPlaying() && player.getCurrentPosition() >= abEndMs) {
            player.seekTo(abStartMs);
        }
    }

    private void showRepeatDialog() {
        int mode = player.getRepeatMode();
        String[] labels = {"Desligado", "Repetir faixa", "Repetir fila"};
        int checked = mode == Player.REPEAT_MODE_ONE ? 1 : mode == Player.REPEAT_MODE_ALL ? 2 : 0;
        new AlertDialog.Builder(this)
                .setTitle("Repetição")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    int target = which == 1 ? Player.REPEAT_MODE_ONE : which == 2 ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF;
                    player.setRepeatMode(target);
                    dialog.dismiss();
                    Toast.makeText(this, "Repetição: " + labels[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showAbRepeatDialog() {
        String start = abStartMs >= 0L ? formatTime(abStartMs) : "não definido";
        String end = abEndMs >= 0L ? formatTime(abEndMs) : "não definido";
        String[] options = {"Definir A em " + formatTime(player.getCurrentPosition()),
                "Definir B em " + formatTime(player.getCurrentPosition()),
                abRepeatEnabled ? "Desativar A-B" : "Ativar A-B", "Limpar A-B"};
        new AlertDialog.Builder(this)
                .setTitle("Repetição A-B")
                .setMessage("A: " + start + "\nB: " + end)
                .setItems(options, (dialog, which) -> {
                    long position = Math.max(0L, player.getCurrentPosition());
                    if (which == 0) {
                        abStartMs = position;
                        if (abEndMs <= abStartMs) abEndMs = -1L;
                        Toast.makeText(this, "Ponto A definido.", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        if (abStartMs < 0L || position <= abStartMs) {
                            Toast.makeText(this, "Defina A antes de B e escolha um ponto depois de A.", Toast.LENGTH_SHORT).show();
                        } else {
                            abEndMs = position;
                            Toast.makeText(this, "Ponto B definido.", Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 2) {
                        if (abStartMs >= 0L && abEndMs > abStartMs) {
                            abRepeatEnabled = !abRepeatEnabled;
                            Toast.makeText(this, abRepeatEnabled ? "A-B ativado." : "A-B desativado.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Defina A e B primeiro.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        abStartMs = -1L;
                        abEndMs = -1L;
                        abRepeatEnabled = false;
                        Toast.makeText(this, "A-B limpo.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showSortDialog() {
        String[] labels = {"Título", "Artista", "Duração curta → longa", "Duração longa → curta"};
        int checked = librarySortMode.equals("artist") ? 1
                : librarySortMode.equals("duration_asc") ? 2
                : librarySortMode.equals("duration_desc") ? 3 : 0;
        new AlertDialog.Builder(this)
                .setTitle("Ordenar biblioteca")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    librarySortMode = which == 1 ? "artist" : which == 2 ? "duration_asc" : which == 3 ? "duration_desc" : "title";
                    getSharedPreferences("auren_player", MODE_PRIVATE).edit()
                            .putString("library_sort", librarySortMode).apply();
                    applyLibrarySort();
                    showHome();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void applyLibrarySort() {
        librarySortMode = getSharedPreferences("auren_player", MODE_PRIVATE)
                .getString("library_sort", librarySortMode);
        Collections.sort(tracks, new Comparator<Track>() {
            @Override public int compare(Track a, Track b) {
                int result;
                if (librarySortMode.equals("artist")) {
                    result = safeArtist(a).compareToIgnoreCase(safeArtist(b));
                } else if (librarySortMode.equals("duration_asc")) {
                    result = Long.compare(a.durationMs, b.durationMs);
                } else if (librarySortMode.equals("duration_desc")) {
                    result = Long.compare(b.durationMs, a.durationMs);
                } else {
                    result = safeTitle(a).compareToIgnoreCase(safeTitle(b));
                }
                if (result != 0) return result;
                return safeTitle(a).compareToIgnoreCase(safeTitle(b));
            }
        });
    }

    private void showEqualizerDialog() {
        if (player == null || player.getAudioSessionId() <= 0) {
            Toast.makeText(this, "Inicie uma música antes de abrir o equalizador.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (equalizer != null) {
                equalizer.release();
                equalizer = null;
            }
            equalizer = new Equalizer(0, player.getAudioSessionId());
            equalizer.setEnabled(true);
            short[] range = equalizer.getBandLevelRange();
            short bands = equalizer.getNumberOfBands();
            int shown = Math.min(5, bands);

            LinearLayout root = column();
            root.setPadding(dp(18), dp(4), dp(18), dp(4));
            TextView note = text("Ajuste as bandas do áudio. A disponibilidade depende do aparelho.", 12, R.color.text_secondary);
            root.addView(note, margins(0, 0, 0, 10));
            for (short i = 0; i < shown; i++) {
                LinearLayout line = row();
                int hz = equalizer.getCenterFreq(i) / 1000;
                TextView label = text((hz >= 1000 ? (hz / 1000) + " kHz" : hz + " Hz"), 11, R.color.text_secondary);
                line.addView(label, new LinearLayout.LayoutParams(dp(58), dp(42)));
                SeekBar band = new SeekBar(this);
                int min = range[0];
                int max = range[1];
                band.setMax(max - min);
                band.setProgress(equalizer.getBandLevel(i) - min);
                final short bandIndex = i;
                band.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && equalizer != null) {
                            equalizer.setBandLevel(bandIndex, (short) (range[0] + progress));
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });
                line.addView(band, new LinearLayout.LayoutParams(0, dp(42), 1));
                root.addView(line);
            }
            TextView reset = text("Repor EQ", 12, R.color.auren_primary);
            reset.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            reset.setPadding(0, dp(8), 0, dp(8));
            reset.setOnClickListener(v -> {
                if (equalizer != null) {
                    short[] r = equalizer.getBandLevelRange();
                    short n = equalizer.getNumberOfBands();
                    short middle = (short) ((r[0] + r[1]) / 2);
                    for (short i = 0; i < n; i++) equalizer.setBandLevel(i, middle);
                }
            });
            root.addView(reset);

            new AlertDialog.Builder(this)
                    .setTitle("Equalizador Auren")
                    .setView(root)
                    .setPositiveButton("Fechar", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "O equalizador não está disponível neste aparelho.", Toast.LENGTH_LONG).show();
        }
    }

    private void releaseEqualizer() {
        if (equalizer != null) {
            try { equalizer.release(); } catch (Exception ignored) {}
            equalizer = null;
        }
    }

'''
    text = text.replace(marker, methods + marker, 1)

# Restore the library sort preference after future Activity recreation.
if 'librarySortMode = getSharedPreferences("auren_player"' not in text:
    needle = '        setContentView(root);\n        showHome();\n    }'
    replacement = '        setContentView(root);\n        librarySortMode = getSharedPreferences("auren_player", MODE_PRIVATE).getString("library_sort", "title");\n        showHome();\n    }'
    if needle not in text:
        raise SystemExit('buildShell ending anchor not found')
    text = text.replace(needle, replacement, 1)

# Add a richer duration field to track details.
old = '                + "\\nArtista: " + safeArtist(track)\n                + "\\nID: " + track.id;'
new = '                + "\\nArtista: " + safeArtist(track)\n                + "\\nDuração: " + formatTime(track.durationMs)\n                + "\\nID: " + track.id;'
text = text.replace(old, new, 1)

# Release audio resources with the Activity.
if 'releaseEqualizer();' not in text:
    on_destroy = '''    @Override protected void onDestroy() {
        releaseEqualizer();
        if (player != null) player.release();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }'''
    end = text.rfind('\n}')
    if end < 0:
        raise SystemExit('class ending not found')
    text = text[:end] + '\n\n' + on_destroy + text[end:]

path.write_text(text)
print('Advanced feature pack applied successfully.')
