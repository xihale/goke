exec(open('gen.py').read().split('# ---------- timetable')[0])
def sw(n,c,fg='#24312D',w=110): return f'<div style="width:{w}px;height:64px;border-radius:16px;background:{c};color:{fg};padding:8px 10px;border:1px solid rgba(0,0,0,.07);font-size:11px;display:flex;flex-direction:column;justify-content:flex-end"><b>{n}</b><span style="opacity:.75">{c}</span></div>'
def block(t):
    T=THEMES[t]; names=['主色 Primary','主色容器','次色容器','三色容器','背景','表面','文字','弱文字']
    vals=[(T['pri'],T['onpri']),(T['pc'],T['opc']),(T['sc'],T['osc']),(T['tc'],T['on']),(T['bg'],T['on']),(T['surf'],T['on']),(T['on'],T['bg']),(T['mut'],T['bg'])]
    return ''.join(sw(n,v,f) for n,(v,f) in zip(names,vals))
def course(t):
    T=THEMES[t]; nm=['天蓝','薄荷青','薰衣草','奶油黄','蜜桃','鼠尾草','玫瑰','雾蓝']
    return ''.join(f'<div style="width:120px;height:54px;border-radius:14px;background:{a};color:{b};padding:8px 10px;font-size:12px;font-weight:600">{n}<div style="font-size:10px;font-weight:400;opacity:.8">{a}</div></div>' for n,(a,b) in zip(nm,T['c']))
def bg(t): return THEMES[t]['bg']
sec=lambda h,b:f'<div style="margin-bottom:28px"><div style="font-size:13px;font-weight:700;color:#7B8B86;letter-spacing:1px;margin-bottom:10px">{h}</div>{b}</div>'
html=f'''<!doctype html><meta charset=utf-8><style>*{{box-sizing:border-box;margin:0;padding:0}}body{{width:1000px;background:#F2F7F5;font-family:'Noto Sans SC',sans-serif;color:#24312D;padding:40px 48px}}.row{{display:flex;gap:10px;flex-wrap:wrap}}</style>
<h1 style="font-size:30px">设计 Token · 清新可爱 × Expressive</h1><p style="color:#7B8B86;margin:6px 0 28px">M0 草案 · 颜色按角色定义，课程色固定语义并与动态取色协调</p>
{sec('品牌配色（默认）',f'<div class=row>{block("brand")}</div>')}
{sec('动态取色示例（壁纸种子色 → 紫调）',f'<div class=row>{block("dynamic")}</div>')}
{sec('深色模式',f'<div class=row style="background:#141A19;padding:12px;border-radius:20px">{block("dark")}</div>')}
{sec('课程色板（浅色）',f'<div class=row>{course("brand")}</div>')}
{sec('课程色板（深色）',f'<div class=row style="background:#141A19;padding:12px;border-radius:20px">{course("dark")}</div>')}
{sec('Dry Run 状态色',f'<div class=row>{sw("新增","rgba(70,161,113,.15)","#46A171",170)}{sw("修改","rgba(213,160,59,.18)","#9A6B12",170)}{sw("删除","rgba(229,100,88,.14)","#E56458",170)}{sw("冲突 / 逾期","#FCE9E7","#E56458")}</div>')}
{sec('形状（大圆角为主，选中态可形变）','<div class=row style="align-items:flex-end"><div style="width:110px;height:70px;background:#fff;border-radius:28px;display:grid;place-items:center;font-size:11px">卡片 28</div><div style="width:110px;height:70px;background:#CDEBE3;border-radius:22px;display:grid;place-items:center;font-size:11px">大容器 22</div><div style="width:110px;height:70px;background:#D9E8EF;border-radius:14px;display:grid;place-items:center;font-size:11px">课程块 14</div><div style="width:110px;height:48px;background:#4FA796;color:#fff;border-radius:24px;display:grid;place-items:center;font-size:11px">胶囊按钮</div><div style="width:60px;height:60px;background:#4FA796;border-radius:20px"></div><div style="width:60px;height:60px;background:#4FA796;border-radius:30px"></div><span style="font-size:11px;color:#7B8B86;padding-bottom:20px">方块 ⇄ 圆形 形变（勾选、FAB）</span></div>')}
{sec('字体与字号','<div style="display:flex;gap:40px"><div><div style="font-size:30px;font-weight:700">10月23日</div><div style="font-size:11px;color:#7B8B86">标题 · 圆润字体（待选） 26–30 / 700</div></div><div><div style="font-size:16px;font-weight:600">概率论与数理统计</div><div style="font-size:11px;color:#7B8B86">正文 · 系统无衬线 14–16 / 500</div></div><div><div style="font-size:11px">教3-204 · 第 1–2 节</div><div style="font-size:11px;color:#7B8B86">辅助 · 10–12 / 400；最小 10</div></div></div>')}
{sec('动效原则','<div style="font-size:13px;line-height:1.9">· 弹性 spring（阻尼 0.7、刚度中等）用于勾选、FAB、视图切换<br>· 勾选：方块 → 圆角填充 + 对勾描边，0.25s；同行文字划线淡出<br>· Dry Run：新增块从 0.9 缩放弹入，删除块淡出并划线，修改块边框闪一下<br>· 尊重系统「减少动画」设置</div>')}
'''
open('tokens.html','w').write(html)
