"""Draws the app icon (Pillow). Usage: python3 tools/make_art.py"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STOPS = [(0.0, (255, 194, 122)), (0.5, (255, 122, 158)), (1.0, (154, 140, 255))]   # sunrise -> pink -> dusk


def gradient(w, h, angle=35):
    """Diagonal three-color gradient."""
    img = Image.new("RGB", (w, h))
    px = img.load()
    a = math.radians(angle)
    dx, dy = math.cos(a), math.sin(a)
    lo = min(0, w * dx, h * dy, w * dx + h * dy)
    hi = max(0, w * dx, h * dy, w * dx + h * dy)
    for y in range(h):
        for x in range(w):
            t = ((x * dx + y * dy) - lo) / (hi - lo)
            for i in range(len(STOPS) - 1):
                t0, c0 = STOPS[i]
                t1, c1 = STOPS[i + 1]
                if t <= t1:
                    f = (t - t0) / (t1 - t0)
                    px[x, y] = tuple(round(c0[k] + (c1[k] - c0[k]) * f) for k in range(3))
                    break
    return img


def sparkle(draw, cx, cy, r, fill):
    """Four-point star."""
    pts = []
    for i in range(8):
        ang = math.pi / 2 * (i // 2) + (0 if i % 2 == 0 else math.pi / 4)
        rad = r if i % 2 == 0 else r * 0.26
        pts.append((cx + rad * math.cos(ang - math.pi / 2), cy + rad * math.sin(ang - math.pi / 2)))
    draw.polygon(pts, fill=fill)


def mark(size, bg=True):
    """The mark: a tablet with a sparkle, on the gradient squircle (bg) or alone (white)."""
    s = size * 4
    img = gradient(s, s).convert("RGBA") if bg else Image.new("RGBA", (s, s), (0, 0, 0, 0))
    if bg:
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, s - 1, s - 1), radius=int(s * 0.23), fill=255)
        img.putalpha(mask)
    layer = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    white = (255, 255, 255, 255)
    # the tablet, in portrait (like the one it is for)
    x0, y0, x1, y1 = s * 0.31, s * 0.14, s * 0.69, s * 0.86
    d.rounded_rectangle((x0, y0, x1, y1), radius=int(s * 0.055), outline=white, width=int(s * 0.05))
    # the camera, on the top edge
    d.ellipse((s * 0.485, s * 0.195, s * 0.515, s * 0.225), fill=white)
    # the sparkle inside the screen
    sparkle(d, s * 0.5, s * 0.53, s * 0.115, white)
    sparkle(d, s * 0.625, s * 0.4, s * 0.042, white)
    if bg:
        shadow = layer.copy().filter(ImageFilter.GaussianBlur(s * 0.02))
        tint = Image.new("RGBA", (s, s), (120, 40, 80, 0))
        tint.putalpha(shadow.getchannel("A").point(lambda v: int(v * 0.22)))
        img.alpha_composite(tint, (0, int(s * 0.012)))
    img.alpha_composite(layer)
    return img.resize((size, size), Image.LANCZOS)


if __name__ == "__main__":
    res = os.path.join(ROOT, "app/res/drawable-nodpi")
    os.makedirs(res, exist_ok=True)
    mark(192).save(os.path.join(res, "icon.png"))
    site = os.path.join(ROOT, "docs/assets")
    os.makedirs(site, exist_ok=True)
    mark(512).save(os.path.join(site, "icon-512.png"))
    mark(180).save(os.path.join(site, "apple-touch-icon.png"))
    mark(64).save(os.path.join(site, "favicon-64.png"))
    print("ok")
