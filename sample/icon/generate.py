#!/usr/bin/env python3
"""
Regenerates every icon the sample app ships from one description of the mark: a 2x2 grid of
rounded tiles -- the gallery -- with one in amber, on the baseline Material purple the sample's
theme already uses.

    python3 sample/icon/generate.py

Standard library only; the renders need `rsvg-convert` (brew install librsvg), the macOS .icns
needs `iconutil`, and the iOS alpha strip needs `sips`. Outputs are committed, so this only has
to run when the mark changes.
"""
import pathlib
import shutil
import struct
import subprocess
import tempfile

SAMPLE = pathlib.Path(__file__).resolve().parent.parent

BACKGROUND = "#6750A4"  # Material baseline primary
TILE = "#EADDFF"        # Material baseline primaryContainer
ACCENT = "#FFB951"

# The grid is two tiles and a gap: 6 + 1 + 6 units wide, corners 1.5 units. Every variant below
# is this shape at a different scale, so a change to the proportions is made once.
TILE_UNITS, GAP_UNITS, RADIUS_UNITS = 6, 1, 1.5
SPAN_UNITS = 2 * TILE_UNITS + GAP_UNITS


def rounded_rect_path(x, y, w, h, r):
    """An SVG / Android-vector path for a rounded rectangle (both take the same syntax)."""
    iw, ih = w - 2 * r, h - 2 * r
    f = lambda v: f"{v:g}"
    return (
        f"M{f(x + r)},{f(y)} h{f(iw)} a{f(r)},{f(r)} 0 0 1 {f(r)},{f(r)} v{f(ih)} "
        f"a{f(r)},{f(r)} 0 0 1 {f(-r)},{f(r)} h{f(-iw)} a{f(r)},{f(r)} 0 0 1 {f(-r)},{f(-r)} "
        f"v{f(-ih)} a{f(r)},{f(r)} 0 0 1 {f(r)},{f(-r)} z"
    )


def tiles(cx, cy, span):
    """The four tiles centred on (cx, cy), as (path, colour) pairs; the accent is bottom-right."""
    u = span / SPAN_UNITS
    tile, gap, r = TILE_UNITS * u, GAP_UNITS * u, RADIUS_UNITS * u
    x0, y0 = cx - span / 2, cy - span / 2
    out = []
    for row in range(2):
        for col in range(2):
            x, y = x0 + col * (tile + gap), y0 + row * (tile + gap)
            colour = ACCENT if (row, col) == (1, 1) else TILE
            out.append((rounded_rect_path(x, y, tile, tile, r), colour))
    return out


def svg(size, body_rect=None, span_fraction=0.56):
    """
    A square SVG [size] px wide. [body_rect] is (x, y, w, h, radius) for the purple body; None
    fills the whole canvas edge to edge, which is what iOS wants (it applies its own mask).
    """
    x, y, w, h, r = body_rect or (0, 0, size, size, 0)
    body = (f'<rect width="{size}" height="{size}" fill="{BACKGROUND}"/>' if r == 0
            else f'<path d="{rounded_rect_path(x, y, w, h, r)}" fill="{BACKGROUND}"/>')
    marks = "".join(f'<path d="{d}" fill="{c}"/>'
                    for d, c in tiles(x + w / 2, y + h / 2, w * span_fraction))
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
            f'viewBox="0 0 {size} {size}">{body}{marks}</svg>\n')


def render(svg_text, px, out, tmp):
    src = tmp / f"{out.stem}-{px}.svg"
    src.write_text(svg_text)
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["rsvg-convert", "-w", str(px), "-h", str(px), str(src), "-o", str(out)], check=True)


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def android():
    res = SAMPLE / "android/src/main/res"
    # Adaptive icons draw a 108dp layer under a mask of at most 72dp, and only the centre 66dp
    # is guaranteed to survive any mask. Clearing the mask is not the same as looking padded in
    # it: a 52dp grid cleared a circle by under 2dp and read as touching the edge, so the grid is
    # 40dp, which leaves about a quarter of the circle's radius around its corners.
    paths = tiles(54, 54, 40)

    def vector(fill_for):
        body = "".join(
            f'    <path\n        android:fillColor="{fill_for(c)}"\n        android:pathData="{d}"/>\n'
            for d, c in paths)
        return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    android:width="108dp"\n    android:height="108dp"\n'
                '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
                f'{body}</vector>\n')

    write(res / "drawable/ic_launcher_foreground.xml", vector(lambda c: c))
    # Themed icons (Android 13+) tint the alpha of this layer, so colour carries nothing here.
    write(res / "drawable/ic_launcher_monochrome.xml", vector(lambda c: "#000000"))
    write(res / "values/ic_launcher_background.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
          f'    <color name="ic_launcher_background">{BACKGROUND}</color>\n</resources>\n')
    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/ic_launcher_background"/>\n'
                '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
                '</adaptive-icon>\n')
    # minSdk is 26, where adaptive icons begin, so no raster fallbacks and no -v26 qualifier.
    write(res / "mipmap-anydpi/ic_launcher.xml", adaptive)
    write(res / "mipmap-anydpi/ic_launcher_round.xml", adaptive)


def ios(tmp):
    out = SAMPLE / "ios/Sources/Assets.xcassets/AppIcon.appiconset/icon-1024.png"
    render(svg(1024), 1024, out, tmp)
    # The App Store rejects an icon with an alpha channel, and rsvg-convert always writes one.
    # A round trip through JPEG drops it.
    jpeg = tmp / "ios.jpg"
    subprocess.run(["sips", "-s", "format", "jpeg", str(out), "--out", str(jpeg)],
                   check=True, capture_output=True)
    subprocess.run(["sips", "-s", "format", "png", str(jpeg), "--out", str(out)],
                   check=True, capture_output=True)


def desktop(tmp):
    # macOS draws a Dock icon as given, so it carries its own margin and corner radius.
    inset = svg(1024, body_rect=(100, 100, 824, 824, 185))
    icons = SAMPLE / "desktop/icons"
    render(inset, 512, SAMPLE / "desktop/src/main/resources/icon.png", tmp)
    render(inset, 512, icons / "icon.png", tmp)

    iconset = tmp / "icon.iconset"
    iconset.mkdir()
    for size in (16, 32, 128, 256, 512):
        render(inset, size, iconset / f"icon_{size}x{size}.png", tmp)
        render(inset, size * 2, iconset / f"icon_{size}x{size}@2x.png", tmp)
    subprocess.run(["iconutil", "-c", "icns", str(iconset), "-o", str(icons / "icon.icns")], check=True)

    # Windows wants the sizes in one .ico; PNG-compressed entries are valid from Vista on.
    sizes = (16, 32, 48, 64, 128, 256)
    pngs = []
    for size in sizes:
        p = tmp / f"ico-{size}.png"
        render(inset, size, p, tmp)
        pngs.append(p.read_bytes())
    header = struct.pack("<HHH", 0, 1, len(sizes))
    offset = len(header) + 16 * len(sizes)
    entries = b""
    for size, data in zip(sizes, pngs):
        entries += struct.pack("<BBBBHHII", size % 256, size % 256, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
    (icons / "icon.ico").write_bytes(header + entries + b"".join(pngs))


def web(tmp):
    # Browser tabs are small, so the body fills the canvas; the SVG is what modern browsers use,
    # the PNG is for the ones that do not take an SVG favicon.
    favicon = svg(1024, body_rect=(0, 0, 1024, 1024, 224), span_fraction=0.6)
    for target in ("jsMain", "wasmJsMain"):
        res = SAMPLE / f"web/src/{target}/resources"
        write(res / "favicon.svg", favicon)
        render(favicon, 64, res / "favicon.png", tmp)


if __name__ == "__main__":
    with tempfile.TemporaryDirectory() as t:
        tmp = pathlib.Path(t)
        android()
        ios(tmp)
        desktop(tmp)
        web(tmp)
    print("icons regenerated")
