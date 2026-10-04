"""16pxの自作item原稿からPNGを生成する開発専用script。通常buildには不要。"""

from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]
PALETTE = {
    '.': (0, 0, 0, 0),
    'o': (26, 32, 44, 255),
    's': (63, 79, 98, 255),
    'm': (119, 148, 162, 255),
    'h': (212, 232, 226, 255),
    't': (35, 100, 108, 255),
    'a': (61, 170, 162, 255),
    'g': (147, 232, 184, 255),
    'p': (119, 61, 166, 255),
    'v': (186, 124, 231, 255),
    'w': (244, 220, 255, 255),
    'b': (113, 66, 39, 255),
    'y': (235, 182, 77, 255),
    'l': (255, 232, 144, 255),
}
# 共通ケースのガラス蓋。上位は工具が輪郭の外へ出るので色以外でも区別できる。
ICONS = {
    'gem_case_upgrade': [
        '................',
        '................',
        '....ooooooo.....',
        '...ohhhhhhmo....',
        '..ohatttttamo...',
        '..ohatgtttamo...',
        '..ohagggttamo...',
        '..ohatgtgtamo...',
        '..ohatttggamo...',
        '..ohattgtgamo...',
        '..omaaaaaaamo...',
        '..osmmmmmmmso...',
        '..osssyhyssso...',
        '...ossssssso....',
        '....ooooooo.....',
        '................',
    ],
    'advanced_gem_case_upgrade': [
        '............ooo.',
        '...........ohhho',
        '....oooooooohhmo',
        '...ohhhhhhmosso.',
        '..ohatttttaoobo.',
        '..ohatvtttamoob.',
        '..ohavwvttamobo.',
        '..ohatvtvtamobo.',
        '..ohattvwwaobo..',
        '..ohattvtvobo...',
        '..omaaaaaobo....',
        '..osmmmmoboso...',
        '..osssyhyssso...',
        '...ossssssso....',
        '....ooooooo.....',
        '................',
    ],
    'gem_case_upgrade_conversion': [
        '................',
        '......oo........',
        '.....ohmo.......',
        '..oo.ohmo.oo....',
        '.ohmoohhmoohmo..',
        '..ohhhhhhhhmo...',
        '..ohmsoosshmo...',
        '.ohhso..osmhho..',
        '.ommsovvoosmmmo.',
        '..osovwwvoosso..',
        '...osovvooyooo..',
        '..ossooooylyo...',
        '.ommoosoylllyo..',
        '..oo..ooylyo....',
        '.......oyo......',
        '........o.......',
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
    preview = [[(32, 35, 42, 255)] * 68 for _ in range(24)]
    for index, (name, rows) in enumerate(ICONS.items()):
        assert len(rows) == 16 and all(len(row) == 16 for row in rows)
        pixels = [[PALETTE[c] for c in row] for row in rows]
        write_png(ROOT / f'neoforge/src/main/resources/assets/sophisticated_gem_case/textures/item/{name}.png', pixels)
        for y, row in enumerate(pixels):
            for x, pixel in enumerate(row):
                if pixel[3]:
                    preview[y + 4][x + 4 + 22 * index] = pixel
    output = ROOT / '.local/verification/acquisition'
    write_png(output / 'icons-original.png', preview)
    scaled = [[pixel for pixel in row for _ in range(8)] for row in preview for _ in range(8)]
    write_png(output / 'icons-preview.png', scaled)


if __name__ == '__main__':
    main()
