from pathlib import Path
import re

path = Path("app/src/main/java/com/auren/musicplayer/MainActivity.java")
text = path.read_text()


def method_ranges(source, name):
    pattern = r"(?m)^\s*@Override\s+protected\s+void\s+" + re.escape(name) + r"\s*\([^)]*\)\s*\{"
    matches = list(re.finditer(pattern, source))
    ranges = []
    for match in matches:
        brace = source.find("{", match.start())
        depth = 0
        for i in range(brace, len(source)):
            if source[i] == "{":
                depth += 1
            elif source[i] == "}":
                depth -= 1
                if depth == 0:
                    ranges.append((match.start(), i + 1, source[brace + 1:i]))
                    break
    return ranges


# The feature generator may append a second onDestroy(). Java permits only one
# method with this signature. Keep the first lifecycle method and merge useful
# cleanup statements from later generated copies into it.
ranges = method_ranges(text, "onDestroy")
if len(ranges) > 1:
    first_start, first_end, first_body = ranges[0]
    merged_body = first_body
    for _, _, body in ranges[1:]:
        for statement in (
            "if (equalizer != null) { equalizer.release(); equalizer = null; }",
            "handler.removeCallbacks(progressUpdater);",
            "if (player != null) { player.release(); player = null; }",
        ):
            if statement in body and statement not in merged_body:
                merged_body += "\n        " + statement + "\n"
    signature = text[first_start:first_end].split("{", 1)[0] + "{"
    replacement = signature + merged_body + "}"
    text = text[:first_start] + replacement + text[first_end:]

    # Recalculate ranges and remove every remaining duplicate after the first.
    ranges = method_ranges(text, "onDestroy")
    for start, end, _ in reversed(ranges[1:]):
        text = text[:start] + text[end:]

path.write_text(text)
count = len(method_ranges(text, "onDestroy"))
if count != 1:
    raise SystemExit(f"Expected exactly one onDestroy(), found {count}")

print("Android lifecycle methods verified: exactly one onDestroy().")
