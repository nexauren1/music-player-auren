from pathlib import Path

path = Path("app/src/main/java/com/auren/musicplayer/MainActivity.java")
text = path.read_text()

# add_feature_pack.py builds Java from Python triple-quoted strings. A Java
# string containing A-B's two-line status message must contain the literal
# escape sequence \\n, not a real newline inside the Java string.
broken = 'setMessage("A: " + start + "' + "\n" + 'B: " + end)'
fixed = 'setMessage("A: " + start + "\\nB: " + end)'

if broken in text:
    text = text.replace(broken, fixed)
    path.write_text(text)

# Fail early if the known malformed Java string is still present.
if broken in text:
    raise SystemExit("Malformed A-B Java string remains")

print("Java syntax repair check passed.")
