"""FireTheDJ 1.2 launch film: builds every scene as an HTML page, in 16:9 and 9:16.

Scenes are devices floating on black with soft stage light, and big clean headlines; captions
are separate transparent pages so the edit can fade them in over the moving devices.
Usage: py -3 film-1.2.py <img dir> <out dir>      (then edit-1.2.js renders and cuts them)
"""
import json
import os
import sys

IMG, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
img = lambda f: "file:///" + os.path.join(IMG, f).replace("\\", "/")

FONT = "'Segoe UI Variable Display','Segoe UI',system-ui,sans-serif"
FIRE = "linear-gradient(90deg,#FF2D55,#FF6A1A 55%,#FFC24A)"
BASE = f"""<!doctype html><html><head><meta charset="utf-8"><style>
html,body{{margin:0;width:100%;height:100%;overflow:hidden}}
body{{font-family:{FONT};color:#F5F5F7;-webkit-font-smoothing:antialiased}}
.fire{{background:{FIRE};-webkit-background-clip:text;background-clip:text;color:transparent}}
.dev{{position:absolute;overflow:hidden;background:#000;box-shadow:0 0 0 2px #2A2A30,0 0 0 9px #0E0E11,0 0 0 10px #3A3A42,0 50px 120px rgba(0,0,0,.65)}}
.dev img{{width:100%;height:100%;object-fit:cover;display:block}}
.phone{{border-radius:7%/3.3%}}
.tv{{border-radius:14px}}
</style></head><body style="background:{{bg}}">{{body}}</body></html>"""

# Stage light behind the devices: soft pools of the app's colours on black.
GLOW = ("radial-gradient(ellipse 60% 55% at 50% 52%,rgba(255,90,31,.20),transparent 70%),"
        "radial-gradient(ellipse 45% 45% at 78% 30%,rgba(194,24,91,.16),transparent 70%),"
        "radial-gradient(ellipse 45% 45% at 22% 75%,rgba(91,43,214,.16),transparent 70%),#000")


def page(body, bg="#000"):
    return BASE.replace("{bg}", bg).replace("{body}", body)


def phone(src, h, x, y, z=1):
    """A phone of height h with its top-left at (x, y); the screen is 0.462 as wide as tall."""
    w = round(h * 0.4621)
    return f'<div class="dev phone" style="left:{x}px;top:{y}px;width:{w}px;height:{h}px;z-index:{z}"><img src="{img(src)}"></div>'


def tv(src, w, x, y, z=1):
    return f'<div class="dev tv" style="left:{x}px;top:{y}px;width:{w}px;height:{round(w * 9 / 16)}px;z-index:{z}"><img src="{img(src)}"></div>'


def centered_phone(src, h, W, y, dx=0, z=1):
    return phone(src, h, round(W / 2 - h * 0.4621 / 2 + dx), y, z)


# ---- scenes: (name, bars, kind, payload) -------------------------------------------------------
SCENES = [
    ("title", 2, "title", None),
    ("new", 1, "headline", ["All-new."]),
    ("glass", 1, "headline", ['<span class="fire">Frosted glass.</span>']),
    ("trio", 2, "devices", "A new look, on phone and TV."),
    ("np", 2, "devices", "A spinning glass record.<br>The live spectrum around it."),
    ("tabs", 2, "devices", "Two tabs. Mix and Music."),
    ("from", 2, "devices", "Mix from any folder or playlist."),
    ("vis", 2, "devices", "Visualizers, now under glass."),
    ("tv", 2, "devices", "FireTheDJ TV. Redesigned too."),
    ("upd", 1, "headline", ['<span class="fire">Updates</span> itself.']),
    ("updates", 2, "devices", "New versions arrive right in the app."),
    ("sup", 1, "headline", ["Support it."]),
    ("support", 2, "devices", "Over UPI, from ₹50. Pick your app."),
    ("tour", 2, "devices", "A new splash. A new tour."),
    ("swipe", 1, "headline", ["Swipe it away.", '<span style="color:#86868B">The music stops.</span>']),
    ("end", 3, "end", None),
]


def devices(name, W, H):
    """The device arrangement for a scene, in a W×H frame."""
    v = H > W
    if name == "trio":
        if v:
            return (centered_phone("02-mix.png", 1000, W, 330, -330, 1) + centered_phone("03-music.png", 1000, W, 330, 330, 1)
                    + centered_phone("04-now-playing.png", 1180, W, 240, 0, 2))
        return (centered_phone("02-mix.png", 740, W, 150, -420, 1) + centered_phone("03-music.png", 740, W, 150, 420, 1)
                + centered_phone("04-now-playing.png", 860, W, 70, 0, 2))
    if name == "np":
        return centered_phone("04-now-playing.png", 1420 if v else 860, W, 150 if v else 50)
    if name == "tabs":
        if v:
            return phone("02-mix.png", 1180, 70, 200, 1) + phone("03-music.png", 1180, 465, 420, 2)
        return centered_phone("02-mix.png", 840, W, 60, -230) + centered_phone("03-music.png", 840, W, 60, 230)
    if name == "from":
        # A close look at the top of the Mix tab: the "From" picker and the mode switch.
        w = 1000 if v else 900
        x = round((W - w) / 2)
        return (f'<div class="dev phone" style="left:{x}px;top:{140 if v else 90}px;width:{w}px;height:{round(w / 0.4621)}px">'
                f'<img src="{img("02-mix.png")}"></div>')
    if name == "vis":
        if v:
            return tv("11-tv-visualizer.png", 980, 50, 1080, 1) + centered_phone("05-visualizer.png", 1000, W, 120, 0, 2)
        return tv("11-tv-visualizer.png", 1180, 120, 140, 1) + phone("05-visualizer.png", 760, 1330, 110, 2)
    if name == "tv":
        if v:
            return tv("09-tv-home.png", 960, 60, 260, 1) + tv("10-tv-player.png", 960, 60, 880, 2)
        return tv("09-tv-home.png", 1060, 120, 70, 1) + tv("10-tv-player.png", 1060, 740, 370, 2)
    if name == "updates":
        if v:
            return phone("14-update.png", 1180, 70, 200, 1) + phone("08-settings.png", 1180, 465, 420, 2)
        return centered_phone("14-update.png", 840, W, 60, -230) + centered_phone("08-settings.png", 840, W, 60, 230)
    if name == "support":
        return centered_phone("13-support.png", 1420 if v else 860, W, 150 if v else 50)
    if name == "tour":
        if v:
            return phone("splash.png", 1180, 70, 200, 1) + phone("tour.png", 1180, 465, 420, 2)
        return centered_phone("splash.png", 840, W, 60, -230) + centered_phone("tour.png", 840, W, 60, 230)
    raise ValueError(name)


def headline(lines, W, H):
    size = 118 if H > W else 132
    rows = "".join(f'<div>{line}</div>' for line in lines)
    return (f'<div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;'
            f'text-align:center;font-weight:600;font-size:{size}px;line-height:1.08;letter-spacing:-.03em">{rows}</div>')


def caption(text, W, H):
    """Bottom caption over a dark fade, transparent elsewhere."""
    v = H > W
    size = 58 if v else 54
    return (f'<div style="position:absolute;left:0;right:0;bottom:0;height:{520 if v else 360}px;'
            f'background:linear-gradient(transparent,rgba(0,0,0,.82) 60%)"></div>'
            f'<div style="position:absolute;left:60px;right:60px;bottom:{150 if v else 70}px;text-align:center;'
            f'font-weight:600;font-size:{size}px;line-height:1.15;letter-spacing:-.02em">{text}</div>')


def title(W, H):
    v = H > W
    return (f'<div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center">'
            f'<img src="{img("Logo.png")}" style="width:{220 if v else 180}px;height:auto;border-radius:22%;box-shadow:0 30px 90px rgba(255,90,31,.35)">'
            f'<div style="margin-top:40px;font-weight:700;font-size:{150 if v else 150}px;letter-spacing:-.04em"><span class="fire">Fire</span>TheDJ</div>'
            f'<div style="margin-top:6px;font-weight:600;font-size:{70 if v else 64}px;color:#86868B;letter-spacing:-.02em">Version 1.2</div></div>')


def end(W, H):
    v = H > W
    return (f'<div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center">'
            f'<img src="{img("Logo.png")}" style="width:{200 if v else 160}px;border-radius:22%;box-shadow:0 30px 90px rgba(255,90,31,.35)">'
            f'<div style="margin-top:34px;font-weight:700;font-size:{120 if v else 112}px;letter-spacing:-.04em"><span class="fire">Fire</span>TheDJ 1.2</div>'
            f'<div style="margin-top:10px;font-weight:600;font-size:{48 if v else 44}px;color:#86868B">For phone and Android TV.</div>'
            f'<div style="margin-top:56px;padding:22px 54px;border-radius:999px;background:{FIRE};font-weight:700;font-size:{44 if v else 38}px">Free on GitHub</div>'
            f'<div style="margin-top:28px;font-family:Consolas,monospace;font-size:{30 if v else 28}px;color:#86868B">github.com/suryablaze02-byte/FireTheDJ</div></div>')


plan = []
for shape, (W, H) in {"h": (1920, 1080), "v": (1080, 1920)}.items():
    for name, bars, kind, payload in SCENES:
        bg = GLOW if kind in ("devices", "title", "end") else "#000"
        if kind == "devices":
            body = devices(name, W, H)
        elif kind == "headline":
            body = headline(payload, W, H)
        elif kind == "title":
            body = title(W, H)
        else:
            body = end(W, H)
        open(os.path.join(OUT, f"{shape}_{name}.html"), "w", encoding="utf-8").write(page(body, bg))
        if kind == "devices":
            open(os.path.join(OUT, f"{shape}_{name}_cap.html"), "w", encoding="utf-8").write(page(caption(payload, W, H), "transparent"))
json.dump([{"name": n, "bars": b, "kind": k} for n, b, k, _ in SCENES], open(os.path.join(OUT, "plan.json"), "w"))
print("ok", sum(b for _, b, _, _ in SCENES), "bars")
