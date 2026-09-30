import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(ROOT, "..", "app", "src", "main", "res")

BACKGROUND = "#006795"
MARK = "#F2F8FC"
LAMP = "#ECBD57"
INK = "#0E212E"

ROOF = [(30, 52), (54, 30), (78, 52)]
WALLS = [(38, 45), (38, 80), (72, 80)]
LAMP_CENTER = (61, 66)
LAMP_RADIUS = 7
STROKE = 8
VIEWPORT = 108


def draw_polyline(draw, points, scale, offset, width, color):
    pts = [(offset + x * scale, offset + y * scale) for x, y in points]
    w = width * scale
    draw.line(pts, fill=color, width=round(w))
    for x, y in pts:
        draw.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=color)


def draw_mark(draw, scale, offset, mark_color, lamp_color):
    draw_polyline(draw, ROOF, scale, offset, STROKE, mark_color)
    draw_polyline(draw, WALLS, scale, offset, STROKE, mark_color)
    cx, cy = LAMP_CENTER
    r = LAMP_RADIUS * scale
    x = offset + cx * scale
    y = offset + cy * scale
    draw.ellipse((x - r, y - r, x + r, y + r), fill=lamp_color)


def render(size, background=None, corner=0.0, mark_scale=1.0, mark_color=MARK, lamp_color=LAMP, shape="square"):
    factor = 4
    big = size * factor
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if background:
        if shape == "circle":
            draw.ellipse((0, 0, big - 1, big - 1), fill=background)
        elif corner > 0:
            draw.rounded_rectangle((0, 0, big - 1, big - 1), radius=big * corner, fill=background)
        else:
            draw.rectangle((0, 0, big, big), fill=background)
    scale = big / VIEWPORT * mark_scale
    offset = (big - VIEWPORT * scale) / 2
    draw_mark(draw, scale, offset, mark_color, lamp_color)
    return image.resize((size, size), Image.LANCZOS)


def save(image, name, folder=ROOT, fmt=None, **kwargs):
    path = os.path.join(folder, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, fmt, **kwargs)


def write_svgs():
    icon = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="1024" height="1024">
  <rect width="108" height="108" rx="24" fill="{BACKGROUND}"/>
  <g fill="none" stroke="{MARK}" stroke-width="{STROKE}" stroke-linecap="round" stroke-linejoin="round">
    <path d="M30 52L54 30L78 52"/>
    <path d="M38 45V80H72"/>
  </g>
  <circle cx="61" cy="66" r="7" fill="{LAMP}"/>
</svg>
"""
    mark_light = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="1024" height="1024">
  <g fill="none" stroke="{BACKGROUND}" stroke-width="{STROKE}" stroke-linecap="round" stroke-linejoin="round">
    <path d="M30 52L54 30L78 52"/>
    <path d="M38 45V80H72"/>
  </g>
  <circle cx="61" cy="66" r="7" fill="#8E5E00"/>
</svg>
"""
    mark_dark = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="1024" height="1024">
  <g fill="none" stroke="{MARK}" stroke-width="{STROKE}" stroke-linecap="round" stroke-linejoin="round">
    <path d="M30 52L54 30L78 52"/>
    <path d="M38 45V80H72"/>
  </g>
  <circle cx="61" cy="66" r="7" fill="{LAMP}"/>
</svg>
"""
    lockup = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 420 108" width="1260" height="324">
  <rect width="108" height="108" rx="24" fill="{BACKGROUND}"/>
  <g fill="none" stroke="{MARK}" stroke-width="{STROKE}" stroke-linecap="round" stroke-linejoin="round">
    <path d="M30 52L54 30L78 52"/>
    <path d="M38 45V80H72"/>
  </g>
  <circle cx="61" cy="66" r="7" fill="{LAMP}"/>
  <text x="130" y="72" font-family="Segoe UI, Roboto, Helvetica, Arial, sans-serif" font-size="52" font-weight="600" fill="{INK}">Livora</text>
</svg>
"""
    for name, content in (
        ("livora-icon.svg", icon),
        ("livora-mark-on-light.svg", mark_light),
        ("livora-mark-on-dark.svg", mark_dark),
        ("livora-logo-horizontal.svg", lockup),
    ):
        with open(os.path.join(ROOT, name), "w", encoding="utf-8", newline="\n") as handle:
            handle.write(content)


def write_brand_pngs():
    save(render(1024, BACKGROUND, corner=0.222), "livora-icon-1024.png")
    save(render(1024, BACKGROUND, corner=0.0), "livora-icon-1024-square.png")
    save(render(512, BACKGROUND, corner=0.0), "livora-icon-512-play-store.png")
    save(render(180, BACKGROUND, corner=0.0), "apple-touch-icon-180.png")
    save(render(192, BACKGROUND, corner=0.222), "icon-192.png")
    save(render(256, BACKGROUND, corner=0.222), "icon-256.png")
    save(render(32, BACKGROUND, corner=0.222), "favicon-32.png")
    save(render(16, BACKGROUND, corner=0.222), "favicon-16.png")
    save(render(1024, None), "livora-mark-transparent-white.png")
    icon_sizes = [16, 32, 48, 64, 128, 256]
    base = render(256, BACKGROUND, corner=0.222)
    base.save(os.path.join(ROOT, "favicon.ico"), format="ICO", sizes=[(s, s) for s in icon_sizes])


def write_lockup_png():
    height = 324
    width = 1260
    canvas = Image.new("RGBA", (width, height), (242, 248, 252, 255))
    icon = render(height, BACKGROUND, corner=0.222)
    canvas.paste(icon, (0, 0), icon)
    font_path = "C:/Windows/Fonts/seguisb.ttf"
    if not os.path.exists(font_path):
        return
    font = ImageFont.truetype(font_path, 156)
    draw = ImageDraw.Draw(canvas)
    draw.text((390, height / 2), "Livora", font=font, fill=INK, anchor="lm")
    save(canvas, "livora-logo-horizontal.png")


def write_android_legacy():
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, size in sizes.items():
        folder = os.path.join(RES, f"mipmap-{density}")
        square = render(size, BACKGROUND, corner=0.16, mark_scale=0.92)
        circle = render(size, BACKGROUND, mark_scale=0.8, shape="circle")
        save(square, "ic_launcher.webp", folder, fmt="WEBP", lossless=True)
        save(circle, "ic_launcher_round.webp", folder, fmt="WEBP", lossless=True)


if __name__ == "__main__":
    write_svgs()
    write_brand_pngs()
    write_lockup_png()
    write_android_legacy()
