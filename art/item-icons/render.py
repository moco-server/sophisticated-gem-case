"""16pxの自作item原稿からPNGを生成する開発専用script。通常buildには不要。"""

from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]
PALETTE = {
    '.': (0, 0, 0, 0),
    'g': (76, 36, 20, 255),
    'y': (255, 218, 64, 255),
}
# 背景はitem modelから元MODの基板を参照する。配布PNGは記号と識別印だけ。
# 共通の結晶は上下左右対称、bbox=(4,3)-(11,12)、中心=(7.5,7.5)。
ICONS = {
    'gem_case_upgrade': [
        '................',
        '................',
        '................',
        '.......gg.......',
        '......gggg......',
        '.....gggggg.....',
        '....gggggggg....',
        '....gggggggg....',
        '....gggggggg....',
        '....gggggggg....',
        '.....gggggg.....',
        '......gggg......',
        '.......gg.......',
        '................',
        '................',
        '................',
    ],
    'advanced_gem_case_upgrade': [
        '................',
        '.yyy........yyy.',
        '.y............y.',
        '.y.....gg.....y.',
        '......gggg......',
        '.....gggggg.....',
        '....gggggggg....',
        '....gggggggg....',
        '....gggggggg....',
        '....gggggggg....',
        '.....gggggg.....',
        '......gggg......',
        '.y.....gg.....y.',
        '.y............y.',
        '.yyy........yyy.',
        '................',
    ],
    'gem_case_upgrade_conversion': [
        '................',
        '................',
        '................',
        '.......gg.......',
        '......gggg......',
        '.....gggggg.....',
        '....gggggggg....',
        '....gggggggg....',
        '....gggggggg....',
        '....gggggggg....',
        '.....gggggg.y...',
        '......gggg.yyy..',
        '.......gg...y...',
        '............y...',
        '................',
        '................',
    ],
}


def write_png(path, pixels):
    height, width = len(pixels), len(pixels[0])
    raw = b''.join(b'\x00' + bytes(c for pixel in row for c in pixel) for row in pixels)

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))


def main():
    preview = [[(190, 190, 182, 255)] * 68 for _ in range(24)]
    for index, (name, rows) in enumerate(ICONS.items()):
        assert len(rows) == 16 and all(len(row) == 16 for row in rows)
        pixels = [[PALETTE[c] for c in row] for row in rows]
        write_png(ROOT / f'neoforge/src/main/resources/assets/sophisticated_gem_case/textures/item/{name}.png', pixels)
        for y, row in enumerate(pixels):
            for x, pixel in enumerate(row):
                if pixel[3]:
                    preview[y + 4][x + 4 + 22 * index] = pixel
    output = ROOT / '.local/verification/item-icons'
    write_png(output / 'icons-original.png', preview)
    scaled = [[pixel for pixel in row for _ in range(8)] for row in preview for _ in range(8)]
    write_png(output / 'icons-preview.png', scaled)


if __name__ == '__main__':
    main()
