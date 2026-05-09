from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont, ImageOps


ROOT = Path(__file__).resolve().parents[1]
TEXTURE_PATH = ROOT / "design" / "generated" / "title_texture_mecha_source.png"
FONT_PATHS = [
    ROOT / "app" / "src" / "main" / "res" / "font" / "smiley_sans_oblique.ttf",
    ROOT / "app" / "src" / "main" / "res" / "font" / "zcool_qingke_huangyou_regular.ttf",
]
OUT_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"


@dataclass(frozen=True)
class TitleSpec:
    text: str
    filename: str
    size: tuple[int, int]
    max_height_ratio: float
    centering: tuple[float, float]
    glow: int
    margin: int = 28


SPECS = [
    TitleSpec(
        text="Alicia 云盘",
        filename="alicia_title_home_mecha.png",
        size=(1760, 430),
        max_height_ratio=0.78,
        centering=(0.46, 0.50),
        glow=20,
    ),
    TitleSpec(
        text="账号管理",
        filename="alicia_title_account_mecha.png",
        size=(1080, 320),
        max_height_ratio=0.72,
        centering=(0.38, 0.44),
        glow=16,
    ),
    TitleSpec(
        text="文件管理",
        filename="alicia_title_files_mecha.png",
        size=(1080, 320),
        max_height_ratio=0.72,
        centering=(0.59, 0.46),
        glow=16,
    ),
]


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONT_PATHS:
        if path.exists():
            try:
                return ImageFont.truetype(str(path), size=size)
            except OSError:
                continue
    raise FileNotFoundError("No usable font file found for title generation.")


def fit_font(text: str, max_width: int, max_height: int) -> ImageFont.FreeTypeFont:
    dummy = Image.new("L", (8, 8), 0)
    draw = ImageDraw.Draw(dummy)
    low, high = 48, 420
    best = load_font(low)
    while low <= high:
        mid = (low + high) // 2
        font = load_font(mid)
        bbox = draw.textbbox((0, 0), text=text, font=font)
        width = bbox[2] - bbox[0]
        height = bbox[3] - bbox[1]
        if width <= max_width and height <= max_height:
            best = font
            low = mid + 1
        else:
            high = mid - 1
    return best


def put_solid(result: Image.Image, mask: Image.Image, color: tuple[int, int, int, int]) -> None:
    layer = Image.new("RGBA", result.size, color)
    layer.putalpha(mask)
    result.alpha_composite(layer)


def multiply_alpha(mask: Image.Image, value: float) -> Image.Image:
    return mask.point(lambda px: max(0, min(255, int(px * value))))


def vertical_gradient(size: tuple[int, int], top: tuple[int, int, int], mid: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    width, height = size
    gradient = Image.new("RGBA", size, (0, 0, 0, 0))
    pixels = gradient.load()
    for y in range(height):
        t = y / max(1, height - 1)
        if t < 0.55:
            local_t = t / 0.55
            src = top
            dst = mid
        else:
            local_t = (t - 0.55) / 0.45
            src = mid
            dst = bottom
        r = int(src[0] + (dst[0] - src[0]) * local_t)
        g = int(src[1] + (dst[1] - src[1]) * local_t)
        b = int(src[2] + (dst[2] - src[2]) * local_t)
        for x in range(width):
            pixels[x, y] = (r, g, b, 255)
    return gradient


def render_panel_lines(size: tuple[int, int], bbox: tuple[int, int, int, int]) -> Image.Image:
    left, top, right, bottom = bbox
    width = right - left
    height = bottom - top
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)

    dark = (15, 33, 66, 155)
    steel = (37, 72, 126, 110)
    cyan = (83, 226, 255, 180)

    for idx, ratio in enumerate((0.16, 0.32, 0.49, 0.66, 0.82)):
        y = int(top + height * ratio)
        start_x = int(left + width * (0.06 + idx * 0.035))
        end_x = min(right - 12, start_x + int(width * (0.16 + (idx % 2) * 0.06)))
        draw.line((start_x, y, end_x, y), fill=dark, width=6)
        draw.line((start_x + 6, y - 10, min(end_x - 12, start_x + int(width * 0.08)), y - 10), fill=steel, width=3)

    for idx, ratio in enumerate((0.22, 0.41, 0.58, 0.76)):
        x = int(left + width * ratio)
        draw.line((x, top + 14, x + 34, bottom - 16), fill=dark, width=5)
        draw.line((x + 6, top + 30, x + 30, bottom - 30), fill=steel, width=2)

    vent_w = max(18, width // 18)
    vent_h = max(8, height // 16)
    for idx, ratio in enumerate((0.12, 0.28, 0.46, 0.64, 0.81)):
        x = int(left + width * ratio)
        y = int(top + height * (0.10 if idx % 2 == 0 else 0.72))
        for step in range(3):
            xx = x + step * (vent_w + 4)
            draw.rounded_rectangle((xx, y, xx + vent_w, y + vent_h), radius=3, fill=cyan if step == 1 else dark)

    return layer


def render_accent_bars(size: tuple[int, int], bbox: tuple[int, int, int, int]) -> Image.Image:
    left, top, right, bottom = bbox
    width = right - left
    height = bottom - top
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)

    accents = [
        (0.18, 0.74, 0.075, 0.085, (255, 186, 33, 255)),
        (0.42, 0.12, 0.055, 0.07, (230, 74, 62, 255)),
        (0.64, 0.76, 0.065, 0.08, (255, 186, 33, 255)),
        (0.86, 0.18, 0.055, 0.07, (230, 74, 62, 255)),
    ]
    for x_ratio, y_ratio, w_ratio, h_ratio, color in accents:
        x = int(left + width * x_ratio)
        y = int(top + height * y_ratio)
        w = int(width * w_ratio)
        h = int(height * h_ratio)
        points = [
            (x, y + h // 2),
            (x + h // 2, y),
            (x + w, y),
            (x + w - h // 2, y + h),
            (x, y + h),
        ]
        draw.polygon(points, fill=color)
    return layer


def trim_alpha(image: Image.Image, padding: int) -> Image.Image:
    bbox = image.getchannel("A").getbbox()
    if bbox is None:
        return image
    left = max(0, bbox[0] - padding)
    top = max(0, bbox[1] - padding)
    right = min(image.width, bbox[2] + padding)
    bottom = min(image.height, bbox[3] + padding)
    return image.crop((left, top, right, bottom))


def build_title(texture: Image.Image, spec: TitleSpec) -> Image.Image:
    canvas = Image.new("RGBA", spec.size, (0, 0, 0, 0))
    text_mask = Image.new("L", spec.size, 0)
    draw = ImageDraw.Draw(text_mask)

    max_width = spec.size[0] - spec.margin * 2
    max_height = int(spec.size[1] * spec.max_height_ratio)
    font = fit_font(spec.text, max_width=max_width, max_height=max_height)
    bbox = draw.textbbox((0, 0), text=spec.text, font=font)
    text_width = bbox[2] - bbox[0]
    text_height = bbox[3] - bbox[1]
    x = spec.margin - bbox[0]
    y = (spec.size[1] - text_height) // 2 - bbox[1]
    draw.text((x, y), text=spec.text, font=font, fill=255)

    body_mask = text_mask.filter(ImageFilter.MinFilter(17))
    edge_mask = ImageChops.subtract(text_mask, body_mask)
    outer_mask = text_mask.filter(ImageFilter.MaxFilter(21))
    outer_ring = ImageChops.subtract(outer_mask, text_mask)

    glow_mask = text_mask.filter(ImageFilter.GaussianBlur(spec.glow))
    put_solid(canvas, multiply_alpha(glow_mask, 0.56), (32, 189, 255, 255))
    put_solid(canvas, multiply_alpha(outer_ring, 0.92), (17, 28, 56, 255))

    texture_fill = ImageOps.fit(texture, spec.size, method=Image.Resampling.LANCZOS, centering=spec.centering)
    texture_fill.putalpha(body_mask)
    canvas.alpha_composite(texture_fill)

    gradient = vertical_gradient(spec.size, (255, 255, 255), (227, 238, 255), (133, 182, 255))
    gradient.putalpha(multiply_alpha(body_mask, 0.24))
    canvas.alpha_composite(gradient)

    top_highlight = ImageChops.subtract(text_mask, ImageChops.offset(text_mask, 0, 9))
    left_highlight = ImageChops.subtract(text_mask, ImageChops.offset(text_mask, 8, 0))
    bottom_shadow = ImageChops.subtract(text_mask, ImageChops.offset(text_mask, 0, -12))
    right_shadow = ImageChops.subtract(text_mask, ImageChops.offset(text_mask, -10, 0))
    put_solid(canvas, multiply_alpha(top_highlight, 0.8), (255, 255, 255, 255))
    put_solid(canvas, multiply_alpha(left_highlight, 0.52), (128, 227, 255, 255))
    put_solid(canvas, multiply_alpha(bottom_shadow, 0.45), (17, 30, 72, 255))
    put_solid(canvas, multiply_alpha(right_shadow, 0.40), (13, 22, 56, 255))

    bbox = body_mask.getbbox() or (0, 0, spec.size[0], spec.size[1])
    panel_lines = render_panel_lines(spec.size, bbox)
    panel_lines.putalpha(ImageChops.multiply(body_mask, panel_lines.getchannel("A")))
    canvas.alpha_composite(panel_lines)

    accent_bars = render_accent_bars(spec.size, bbox)
    accent_bars.putalpha(ImageChops.multiply(body_mask, accent_bars.getchannel("A")))
    canvas.alpha_composite(accent_bars)

    cyan_edge = ImageChops.subtract(edge_mask.filter(ImageFilter.MaxFilter(5)), body_mask.filter(ImageFilter.MinFilter(3)))
    put_solid(canvas, multiply_alpha(cyan_edge, 0.76), (66, 219, 255, 255))
    put_solid(canvas, multiply_alpha(edge_mask, 0.70), (248, 252, 255, 255))

    return trim_alpha(canvas, padding=26)


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    texture = Image.open(TEXTURE_PATH).convert("RGBA")
    for spec in SPECS:
        image = build_title(texture, spec)
        output_path = OUT_DIR / spec.filename
        image.save(output_path)
        print(f"{spec.filename}: {image.width}x{image.height}")


if __name__ == "__main__":
    main()
