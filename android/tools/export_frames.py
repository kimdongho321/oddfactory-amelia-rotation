"""앱용 프레임 내보내기: art/seq.json의 재생 모션 프레임을 흰 배경 제거(테두리에서 이어진 흰색만)한
투명 WebP로 바꿔 app/build/generated/frames/assets/f/ 에 넣고, motions.json 과 런처 아이콘을 만든다.
빌드(preBuild) 때 자동 실행. 원본 그림·작업 메모는 APK에 넣지 않는다."""
import os, sys, json
import numpy as np
from PIL import Image
from scipy import ndimage as nd
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))      # android/
proj = os.path.dirname(root)
play = os.path.join(proj, 'art', 'play')
out = os.path.join(root, 'app', 'build', 'generated', 'frames')
assets = os.path.join(out, 'assets', 'f'); os.makedirs(assets, exist_ok=True)
SIZE = 360
KEEP = {'대기': 'idle', '등장': 'enter', '퇴장': 'exit', '홀로그램': 'holo',
        '뜨거운 아이스 아메리카노': 'coffee', '회전 바꿔치기(빨려감)': 'swapout', '회전 바꿔치기(나옴)': 'swapin', '걷기': 'walk',
        '볼 꼬집기': 'pinch', '블루투스': 'bt', '볼 꼬집기 끝(넘어졌다 일어남)': 'pinchend'}
seq = json.load(open(os.path.join(proj, 'art', 'seq.json'), encoding='utf-8')); seq.pop('_설명', None)
def cutout(im):
    a = np.asarray(im.convert('RGB'))
    white = a.min(-1) > 236
    lab, _ = nd.label(white)
    border = set(np.unique(np.r_[lab[0], lab[-1], lab[:, 0], lab[:, -1]])) - {0}
    bg = np.isin(lab, list(border))
    alpha = np.where(bg, 0, 255).astype('uint8')
    return Image.fromarray(np.dstack([a, alpha]), 'RGBA')
motions = {}; done = set()
for ch, ms in seq.items():
    motions[ch] = {}
    for kr, en in KEEP.items():
        if kr not in ms: continue
        fs = ms[kr]; motions[ch][en] = [f'{ch}_{f}' for f in fs]
        for f in fs:
            n = f'{ch}_{f}'
            if n in done: continue
            done.add(n)
            dst = os.path.join(assets, n + '.webp')
            src = os.path.join(play, n + '.png')
            if os.path.exists(dst) and os.path.getmtime(dst) >= os.path.getmtime(src): continue
            cutout(Image.open(src)).resize((SIZE, SIZE), Image.LANCZOS).save(dst, 'WEBP', lossless=True, quality=90)
# 쓰지 않는 옛 프레임 지우기
for f in os.listdir(assets):
    if f[:-5] not in done: os.remove(os.path.join(assets, f))
json.dump(motions, open(os.path.join(out, 'assets', 'motions.json'), 'w', encoding='utf-8'), ensure_ascii=False)
# 블루투스 기기 아이콘(art/fx/devices.png 3x3) → assets/dev/<이름>.webp, 테두리 흰 배경만 투명
dev_dir = os.path.join(out, 'assets', 'dev'); os.makedirs(dev_dir, exist_ok=True)
_dv = Image.open(os.path.join(proj, 'art', 'fx', 'devices.png')).convert('RGB')
for k, nm in enumerate(['buds', 'headphones', 'watch', 'speaker', 'keyboard', 'mouse', 'pc', 'gamepad', 'car']):
    cw = _dv.width // 3; cell = _dv.crop(((k % 3) * cw, (k // 3) * cw, (k % 3 + 1) * cw, (k // 3 + 1) * cw))
    cut = cutout(cell); _c = np.asarray(cell).astype(int)
    _A = nd.binary_fill_holes(nd.binary_closing(_c.min(-1) < 120, iterations=6)) | (np.asarray(cut)[..., 3] > 0) & nd.binary_fill_holes(nd.binary_closing(_c.min(-1) < 120, iterations=6))      # 기기 안쪽 흰 면이 비치지 않게
    cut.putalpha(Image.fromarray(np.where(_A, 255, 0).astype('uint8'))); bb = cut.getchannel('A').getbbox(); cut = cut.crop(bb)
    sq = Image.new('RGBA', (max(cut.size),) * 2, (0, 0, 0, 0)); sq.paste(cut, ((sq.width - cut.width) // 2, (sq.height - cut.height) // 2), cut)
    sq.resize((256, 256), Image.NEAREST).save(os.path.join(dev_dir, nm + '.webp'), 'WEBP', lossless=True)
# 블루투스 작업 드론(art/fx/drone.png, 투명) → assets/fx/drone.webp
os.makedirs(os.path.join(out, 'assets', 'fx'), exist_ok=True)
for _src, _dst in [('drone.png', 'drone_r41.webp'), ('drone_amelia.png', 'drone_amelia.webp')]:
    Image.open(os.path.join(proj, 'art', 'fx', _src)).convert('RGBA').resize((192, 192), Image.NEAREST).save(os.path.join(out, 'assets', 'fx', _dst), 'WEBP', lossless=True)
# 런처 아이콘: ODD FACTORY '발명품 배송 상자'(두두 stage_release.icon과 같은 규격). 원본 art/icon/launcher-box-128.png
res = os.path.join(out, 'res')
fg = Image.open(os.path.join(proj, 'art', 'icon', 'launcher-box-128.png')).convert('RGBA')
assert fg.size == (128, 128)
bb = fg.getchannel('A').getbbox()
assert bb and 24 <= bb[0] < bb[2] <= 104 and 24 <= bb[1] < bb[3] <= 104, bb
def put(rel, im):
    path = os.path.join(res, rel); os.makedirs(os.path.dirname(path), exist_ok=True); im.save(path)
put('drawable-nodpi/of_box_foreground.png', fg.resize((256, 256), Image.NEAREST))
os.makedirs(os.path.join(res, 'mipmap-anydpi-v26'), exist_ok=True)
XML = ('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">' + chr(10)
       + '    <background android:drawable="@color/of_box_launcher_cream" />' + chr(10)
       + '    <foreground android:drawable="@drawable/of_box_foreground" />' + chr(10) + '</adaptive-icon>' + chr(10))
open(os.path.join(res, 'mipmap-anydpi-v26', 'ic_launcher.xml'), 'w', encoding='utf-8').write(XML)
os.makedirs(os.path.join(res, 'values'), exist_ok=True)
open(os.path.join(res, 'values', 'of_box_launcher_colors.xml'), 'w', encoding='utf-8').write(
    '<resources><color name="of_box_launcher_cream">#FFF6ED</color></resources>' + chr(10))
sq = Image.new('RGBA', (128, 128), '#FFF6ED'); sq.alpha_composite(fg)
for folder, px in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    put(f'mipmap-{folder}/ic_launcher.png', sq.resize((px, px), Image.NEAREST))
print('frames', len(done))
