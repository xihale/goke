import json
THEMES = {
 'brand': dict(bg='#F2F7F5', surf='#FFFFFF', on='#24312D', mut='#7B8B86', out='#E4ECE9', pri='#4FA796', onpri='#FFFFFF', pc='#CDEBE3', opc='#1B473F', sc='#FBE3D6', osc='#7A4630', tc='#E4E0F6', nav='#FFFFFF', code='#F4F8F7',
   c=[('#D9E8EF','#3B6173'),('#BFDCD5','#2C5B53'),('#DAD8EE','#4F4D7C'),('#F3EBCF','#75662F'),('#F7DFD2','#85503A'),('#DFE8DA','#4B5F49'),('#F4D9E0','#83465A'),('#D2D8EA','#454F7A')]),
 'dynamic': dict(bg='#F6F3FA', surf='#FFFFFF', on='#2A2633', mut='#847D92', out='#E9E4F0', pri='#7A6CC4', onpri='#FFFFFF', pc='#E3DEFA', opc='#2F2764', sc='#F6DDE8', osc='#6E3550', tc='#D8EAF6', nav='#FFFFFF', code='#F6F3FA',
   c=[('#DCE6F5','#38557D'),('#CFE5E3','#2E5C5A'),('#E3DAF4','#56437F'),('#F1E8D3','#725F35'),('#F5DAE0','#83435A'),('#DFEAD9','#486247'),('#F3D8EA','#7E3F6B'),('#D6D9F0','#454A83')]),
 'dark': dict(bg='#141A19', surf='#1E2624', on='#E6EEEB', mut='#93A39E', out='#2A3431', pri='#7CCDBA', onpri='#0B2A24', pc='#24413B', opc='#BFEDE2', sc='#4A3329', osc='#F6D5C3', tc='#2F2C45', nav='#1E2624', code='#18201E',
   c=[('#2B4350','#BFE0F0'),('#24463F','#B2E3D8'),('#38355A','#D3D0F5'),('#4A4224','#F0E5B6'),('#503629','#F5D2BF'),('#33442F','#CFE5C8'),('#56323E','#F4CBD7'),('#303A5E','#C9D3F5')]),
}
def css(t):
    T=THEMES[t]
    v=''.join(f'--b{i}:{a};--f{i}:{b};' for i,(a,b) in enumerate(T['c']))
    return f""":root{{--bg:{T['bg']};--surf:{T['surf']};--on:{T['on']};--mut:{T['mut']};--out:{T['out']};--pri:{T['pri']};--onpri:{T['onpri']};--pc:{T['pc']};--opc:{T['opc']};--sc:{T['sc']};--osc:{T['osc']};--tc:{T['tc']};--code:{T['code']};--add:#46A171;--mod:#D5A03B;--del:#E56458;{v}}}
*{{box-sizing:border-box;margin:0;padding:0}}
body{{width:390px;height:844px;overflow:hidden;background:var(--bg);color:var(--on);font-family:'Noto Sans SC','Noto Sans CJK SC',sans-serif;font-size:14px;line-height:1.4;position:relative}}
.sb{{height:28px;display:flex;justify-content:space-between;align-items:center;padding:6px 22px 0;font-size:12px;font-weight:600}}
.bar{{display:flex;align-items:center;justify-content:space-between;padding:6px 16px 8px;height:48px}}
.bar h1{{font-size:22px;font-weight:700;letter-spacing:.5px}}
.ib{{width:36px;height:36px;border-radius:14px;background:var(--surf);display:grid;place-items:center}}
.ib svg,.nav svg{{width:20px;height:20px;stroke:var(--on);fill:none;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}}
.nav{{position:absolute;left:12px;right:12px;bottom:14px;height:62px;border-radius:26px;background:{T['nav']};box-shadow:0 2px 10px rgba(0,0,0,.07);display:flex;justify-content:space-around;align-items:center;padding:0 6px}}
.nav div{{display:flex;flex-direction:column;align-items:center;gap:2px;font-size:10px;color:var(--mut);width:76px;height:48px;justify-content:center;border-radius:20px}}
.nav div.on{{background:var(--pc);color:var(--opc);font-weight:600}}
.nav div.on svg{{stroke:var(--opc)}}
.nav svg{{stroke:var(--mut)}}
.ico svg{{width:100%;height:100%;stroke:var(--on);fill:none;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}}
.card{{background:var(--surf);border-radius:22px}}
.chip{{display:inline-flex;align-items:center;gap:4px;height:24px;padding:0 10px;border-radius:12px;font-size:11px;font-weight:500;background:var(--pc);color:var(--opc)}}
"""
ICON={
'today':'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4"/></svg>',
'grid':'<svg viewBox="0 0 24 24"><rect x="4" y="4" width="7" height="7" rx="2"/><rect x="13" y="4" width="7" height="7" rx="2"/><rect x="4" y="13" width="7" height="7" rx="2"/><rect x="13" y="13" width="7" height="7" rx="2"/></svg>',
'cal':'<svg viewBox="0 0 24 24"><rect x="4" y="5" width="16" height="15" rx="4"/><path d="M4 10h16M9 3v4M15 3v4"/></svg>',
'todo':'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8"/><path d="M8.5 12.3l2.4 2.4 4.6-5"/></svg>',
'code':'<svg viewBox="0 0 24 24"><path d="M9 8l-4 4 4 4M15 8l4 4-4 4M13.5 6l-3 12"/></svg>',
'plus':'<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
'user':'<svg viewBox="0 0 24 24"><circle cx="12" cy="9" r="3.5"/><path d="M5 20c1-4 4-5 7-5s6 1 7 5"/></svg>',
'chev':'<svg viewBox="0 0 24 24"><path d="M9 6l6 6-6 6"/></svg>',
'pen':'<svg viewBox="0 0 24 24"><path d="M5 19l1-4L16.5 4.5a2 2 0 0 1 3 3L9 18z"/><path d="M14.5 6.5l3 3"/></svg>',
'x':'<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg>',
'file':'<svg viewBox="0 0 24 24"><path d="M7 3h7l4 4v14H7z"/><path d="M14 3v4h4M9.5 13h5M9.5 17h5"/></svg>',
'school':'<svg viewBox="0 0 24 24"><path d="M3 9l9-5 9 5-9 5z"/><path d="M7 11.5V16c3 2 7 2 10 0v-4.5"/></svg>',
'clock':'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8"/><path d="M12 7.5V12l3 2"/></svg>',
'copy':'<svg viewBox="0 0 24 24"><rect x="8" y="8" width="11" height="11" rx="3"/><path d="M16 8V6a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h1"/></svg>',
'spark':'<svg viewBox="0 0 24 24"><path d="M12 4l1.8 4.6L18.5 10l-4.7 1.4L12 16l-1.8-4.6L5.5 10l4.7-1.4zM18 16l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z"/></svg>',
'back':'<svg viewBox="0 0 24 24"><path d="M15 6l-6 6 6 6"/></svg>',
}
def nav(active):
    items=[('today','今日','today'),('grid','课表','grid'),('cal','日历','cal'),('todo','待办','todo')]
    return '<div class="nav">'+''.join(f'<div class="{"on" if k==active else ""}">{ICON[i]}<span>{n}</span></div>' for i,n,k in items)+'</div>'
def status(): return '<div class="sb"><span>9:41</span><span style="display:flex;gap:5px;align-items:center"><i style="display:block;width:14px;height:9px;background:linear-gradient(90deg,var(--on) 0 20%,transparent 20% 27%,var(--on) 27% 47%,transparent 47% 54%,var(--on) 54% 74%,transparent 74% 81%,var(--on) 81%);clip-path:polygon(0 100%,100% 100%,100% 0)"></i><i style="display:block;width:20px;height:10px;border:1.5px solid var(--on);border-radius:3px;padding:1px"><b style="display:block;height:100%;width:70%;background:var(--on);border-radius:1px"></b></i></span></div>'
def page(t,body,extra=''):
    open(f'{t[1]}.html','w').write(f'<!doctype html><meta charset=utf-8><style>{css(t[0])}{extra}</style>{body}')

# ---------- timetable
PT=['08:30','09:20','10:25','11:15','13:50','14:40','15:30','16:30','17:20','18:30','19:20','20:10']
H=50; GAP=8; groups=[(1,4),(5,9),(10,12)]
def ytop(p):
    g=sum(1 for a,b in groups if b<p); return (p-1)*H+g*GAP
COURSES=[(0,1,'概率论与数理统计','教3-204',1,2,0),(0,6,'数字逻辑与系统设计','教2-225',6,7,5),
 (1,2,'数据结构','教2-321',1,2,1),(1,3,'工程英语','实3-611',3,4,3),(1,7,'劳动教育','',6,6,6),
 (2,2,'数据结构','教2-512',1,2,1),(2,5,'体育(3)','',3,4,4),(2,10,'3D打印综合训练','实4-201',10,12,3),
 (3,3,'大学物理(2)','教1-401',1,2,2),(3,3,'数字逻辑与系统设计','教5-105',3,4,5),(3,10,'西方哲学导论','教4-310',10,12,2),
 (4,1,'概率论与数理统计','教3-204',1,2,0),(4,8,'大学生心理健康教育','教3-110',6,7,7)]
def timetable(theme,fname,title='大一下 · 第 1 周',today=2,extra_blocks='',showtitle=True,nav_on=True):
    left=44; gx=8; colw=(390-2*gx-left-4)/7
    gh=ytop(12)+H
    cols=''.join(f'<div style="position:absolute;left:{left+i*colw+3}px;width:{colw-6}px;top:0;height:26px;line-height:26px;text-align:center;font-size:12px;border-radius:13px;font-weight:{700 if i==today else 500};background:{"var(--pri)" if i==today else "transparent"};color:{"var(--onpri)" if i==today else "var(--on)"}">周{"一二三四五六日"[i]}</div>' for i in range(7))
    todaycol=f'<div style="position:absolute;left:{left+today*colw+1}px;width:{colw-2}px;top:0;bottom:0;background:var(--pc);opacity:.35;border-radius:14px"></div>'
    rows=''
    for p in range(1,13):
        y=ytop(p)
        rows+=f'<div style="position:absolute;left:0;top:{y}px;width:{left}px;height:{H}px;text-align:center;padding-top:5px"><div style="font-size:14px;font-weight:700;line-height:1.1">{p}</div><div style="font-size:8.5px;color:var(--mut);line-height:1.2">{PT[p-1]}</div></div>'
        rows+=f'<div style="position:absolute;left:{left}px;right:4px;top:{y+H-1}px;height:1px;background:var(--out);opacity:.7"></div>'
    blocks=''
    for d,_,name,room,a,b,c in COURSES:
        y=ytop(a)+2; h=ytop(b)+H-ytop(a)-4
        r=f'<div style="font-size:8.5px;opacity:.8;margin-top:3px;line-height:1.2">{room}</div>' if room and b>a else ''
        blocks+=f'<div style="position:absolute;left:{left+d*colw+2}px;width:{colw-4}px;top:{y}px;height:{h}px;border-radius:13px;background:var(--b{c});color:var(--f{c});padding:6px 4px;overflow:hidden"><div style="font-size:10.5px;font-weight:600;line-height:1.25">{name}</div>{r}</div>'
    body=f'''{status()}<div class="bar"><h1>{title}</h1><div style="display:flex;gap:8px"><div class="ib">{ICON['pen']}</div><div class="ib">{ICON['user']}</div></div></div>
<div style="position:absolute;left:{gx}px;right:{gx}px;top:92px;height:{gh+60}px" class="card"></div>
<div style="position:absolute;left:{gx}px;right:{gx}px;top:100px">{cols}<div style="position:absolute;top:36px;left:0;right:0">{todaycol}{rows}{blocks}{extra_blocks}</div></div>{nav('grid') if nav_on else ''}'''
    page((theme,fname),body)
timetable('brand','timetable_brand')
timetable('dynamic','timetable_dynamic')
timetable('dark','timetable_dark')

# ---------- today
def ring(done,total,size=54):
    import math
    r=size/2-5; c=2*math.pi*r; f=c*done/total
    return f'<svg width="{size}" height="{size}" viewBox="0 0 {size} {size}"><circle cx="{size/2}" cy="{size/2}" r="{r}" fill="none" stroke="var(--out)" stroke-width="6"/><circle cx="{size/2}" cy="{size/2}" r="{r}" fill="none" stroke="var(--pri)" stroke-width="6" stroke-linecap="round" stroke-dasharray="{f} {c}" transform="rotate(-90 {size/2} {size/2})"/><text x="50%" y="55%" text-anchor="middle" dominant-baseline="middle" font-size="13" font-weight="700" fill="var(--on)">{done}/{total}</text></svg>'
def tl(time,end,title,sub,c,kind='course',extra=''):
    return f'''<div style="display:flex;gap:10px;margin-bottom:10px"><div style="width:40px;text-align:right;font-size:12px;font-weight:600;padding-top:10px">{time}<div style="font-size:10px;color:var(--mut);font-weight:400">{end}</div></div>
<div style="flex:1;border-radius:18px;background:var(--b{c});color:var(--f{c});padding:10px 12px;{extra}"><div style="font-weight:600;font-size:14px">{title}</div><div style="font-size:11px;opacity:.85;margin-top:2px">{sub}</div></div></div>'''
def check(on):
    svg='<svg viewBox="0 0 24 24" width="14" height="14" style="stroke:var(--onpri);fill:none;stroke-width:3;stroke-linecap:round;stroke-linejoin:round"><path d="M5 12.5l4.5 4.5L19 7"/></svg>' if on else ''
    return f'<div style="width:22px;height:22px;border-radius:8px;flex:none;border:2px solid {"var(--pri)" if on else "var(--mut)"};background:{"var(--pri)" if on else "transparent"};display:grid;place-items:center">{svg}</div>'
body=f'''{status()}<div class="bar" style="height:64px"><div><div style="font-size:12px;color:var(--mut)">周五 · 第 1 周</div><h1 style="font-size:26px">10月23日</h1></div><div class="ib">{ICON['user']}</div></div>
<div style="padding:0 16px">
<div class="card" style="display:flex;align-items:center;gap:14px;padding:12px 16px;margin-bottom:14px;background:var(--pc)">{ring(1,4,58)}<div style="flex:1"><div style="font-weight:700;color:var(--opc)">今天有 2 节课，3 件待办</div><div style="font-size:12px;color:var(--opc);opacity:.8">下午 16:30 有一段学习时间块</div></div></div>
{tl('08:30','10:05','概率论与数理统计','教3-204 · 第 1–2 节',0)}
<div style="display:flex;gap:10px;margin-bottom:10px"><div style="width:40px"></div><div class="card" style="flex:1;padding:9px 12px;display:flex;gap:10px;align-items:center">{check(False)}<div style="flex:1"><div style="font-size:13px;font-weight:500">交概率论第三章作业</div><div style="font-size:11px;color:var(--mut)">下节课前 · 概率论</div></div><span class="chip" style="background:var(--sc);color:var(--osc)">高</span></div></div>
{tl('14:40','16:15','大学生心理健康教育','教3-110 · 第 6–7 节',7)}
{tl('16:30','17:30','数据结构实验报告','待办时间块 · 预计 1h · 子任务 2/3',1,extra='border:2px dashed var(--f1);background:transparent')}
<div style="display:flex;gap:10px;margin-bottom:10px"><div style="width:40px;text-align:right;font-size:12px;font-weight:600;padding-top:10px">20:00</div><div style="flex:1;border-radius:18px;background:var(--sc);color:var(--osc);padding:10px 12px"><div style="font-weight:600">社团例会</div><div style="font-size:11px;opacity:.85">日程 · 学生活动中心</div></div></div>
<div style="display:flex;gap:10px"><div style="width:40px"></div><div class="card" style="flex:1;padding:9px 12px;display:flex;gap:10px;align-items:center">{check(True)}<div style="font-size:13px;color:var(--mut);text-decoration:line-through">取快递</div></div></div>
<div style="margin-top:16px;font-size:12px;color:var(--mut);font-weight:600;margin-left:4px">明天 · 周六</div>
<div class="card" style="margin-top:6px;padding:12px 14px;font-size:13px;color:var(--mut)">没有课程，留给自己吧 ☕　<span style="color:var(--on)">1 件待办到期</span></div>
</div>{nav('today')}'''
page(('brand','today'),body)

# ---------- calendar month
import calendar
cells=''
days=[28,29,30]+list(range(1,32))+[1,2,3,4,5,6,7,8]
dots={5:['b1'],8:['b1','b4'],12:['b2'],15:['b3'],19:['b0','b5'],21:['b4'],22:['b1'],23:['b0','b7','b1'],26:['b3'],28:['b2'],30:['b6']}
for i,d in enumerate(days[:35]):
    other = (i<3) or (i>=34)
    sel = (not other and d==23); tod=sel
    dd=''.join(f'<i style="width:5px;height:5px;border-radius:3px;background:var(--f{k[1]})"></i>' for k in dots.get(d,[]) if not other)
    st=f'background:var(--pri);color:var(--onpri);border-radius:16px;font-weight:700' if sel else ('color:var(--mut);opacity:.5' if other else '')
    cells+=f'<div style="height:46px;display:flex;flex-direction:column;align-items:center;padding-top:4px"><div style="width:34px;height:30px;display:grid;place-items:center;font-size:14px;{st}">{d}</div><div style="display:flex;gap:2px;margin-top:3px">{dd}</div></div>'
wk=''.join(f'<div style="text-align:center;font-size:11px;color:var(--mut);padding:6px 0">{w}</div>' for w in '一二三四五六日')
body=f'''{status()}<div class="bar"><h1>2026年10月</h1><div style="display:flex;gap:6px"><div style="display:flex;background:var(--surf);border-radius:14px;padding:3px;font-size:12px"><span style="padding:5px 9px;border-radius:11px;background:var(--pc);color:var(--opc);font-weight:600">月</span><span style="padding:5px 9px;color:var(--mut)">周</span><span style="padding:5px 9px;color:var(--mut)">日</span></div></div></div>
<div style="padding:0 12px"><div class="card" style="padding:6px 8px 8px"><div style="display:grid;grid-template-columns:repeat(7,1fr)">{wk}{cells}</div></div>
<div style="display:flex;justify-content:space-between;align-items:baseline;margin:14px 6px 8px"><b style="font-size:16px">10月23日 周五</b><span style="font-size:12px;color:var(--mut)">2 节课 · 1 日程 · 3 待办</span></div>
<div style="display:flex;flex-direction:column;gap:8px">
<div style="display:flex;gap:10px;border-radius:16px;background:var(--b0);color:var(--f0);padding:10px 12px"><div style="font-size:12px;font-weight:600;width:40px">08:30</div><div><b style="font-size:13px">概率论与数理统计</b><div style="font-size:11px;opacity:.85">教3-204</div></div></div>
<div style="display:flex;gap:10px;border-radius:16px;background:var(--b7);color:var(--f7);padding:10px 12px"><div style="font-size:12px;font-weight:600;width:40px">14:40</div><div><b style="font-size:13px">大学生心理健康教育</b><div style="font-size:11px;opacity:.85">教3-110</div></div></div>
<div class="card" style="display:flex;gap:10px;padding:10px 12px;align-items:center;border:1.5px dashed var(--pri)"><div style="font-size:12px;font-weight:600;width:40px">16:30</div><div style="flex:1"><b style="font-size:13px">数据结构实验报告</b><div style="font-size:11px;color:var(--mut)">待办时间块 · 1h</div></div>{check(False)}</div>
<div style="display:flex;gap:10px;border-radius:16px;background:var(--sc);color:var(--osc);padding:10px 12px"><div style="font-size:12px;font-weight:600;width:40px">20:00</div><div><b style="font-size:13px">社团例会</b><div style="font-size:11px;opacity:.85">学生活动中心</div></div></div>
</div></div>{nav('cal')}'''
page(('brand','calendar'),body)

# ---------- todo
def todo(title,sub,on=False,chips='',prog=None,late=False):
    pr=f'<div style="height:5px;border-radius:3px;background:var(--out);margin-top:6px"><div style="width:{prog}%;height:100%;border-radius:3px;background:var(--pri)"></div></div>' if prog else ''
    return f'<div class="card" style="display:flex;gap:12px;padding:12px 14px;align-items:flex-start;margin-bottom:8px"><div style="margin-top:2px">{check(on)}</div><div style="flex:1"><div style="font-size:14px;font-weight:500;{"text-decoration:line-through;color:var(--mut)" if on else ""}">{title}</div><div style="font-size:11px;color:{"var(--del)" if late else "var(--mut)"};margin-top:2px">{sub}</div>{pr}<div style="display:flex;gap:6px;margin-top:6px;flex-wrap:wrap">{chips}</div></div></div>'
cc=lambda t,c:f'<span class="chip" style="background:var(--b{c});color:var(--f{c})">{t}</span>'
body=f'''{status()}<div class="bar"><h1>待办</h1><div class="ib">{ICON['user']}</div></div>
<div style="padding:0 16px"><div style="display:flex;gap:6px;margin-bottom:12px">{''.join(f'<span style="padding:7px 14px;border-radius:16px;font-size:13px;{"background:var(--pri);color:var(--onpri);font-weight:600" if i==0 else "background:var(--surf);color:var(--mut)"}">{t}</span>' for i,t in enumerate(['今天','计划','全部','已完成']))}</div>
<div style="font-size:12px;color:var(--del);font-weight:600;margin:4px 4px 8px">逾期 · 1</div>
{todo('提交 3D 打印方案','逾期 1 天 · 10月22日 18:00',late=True,chips=cc('3D打印综合训练',3))}
<div style="font-size:12px;color:var(--mut);font-weight:600;margin:12px 4px 8px">今天 · 3</div>
{todo('交概率论第三章作业','下节课前 · 周一 08:30',chips=cc('概率论',0)+'<span class="chip" style="background:var(--sc);color:var(--osc)">高优先级</span>')}
{todo('数据结构实验报告','今天 16:30 – 17:30 · 已排入日历',prog=66,chips=cc('数据结构',1)+'<span class="chip">子任务 2/3</span>')}
{todo('买 3D 打印耗材','今天','',chips='<span class="chip" style="background:var(--tc);color:var(--on)">生活</span>')}
<div style="font-size:12px;color:var(--mut);font-weight:600;margin:12px 4px 8px">已完成 · 1</div>
{todo('取快递','今天 12:10',on=True)}
</div>
<div style="position:absolute;right:20px;bottom:92px;width:60px;height:60px;border-radius:20px;background:var(--pri);display:grid;place-items:center;box-shadow:0 4px 14px rgba(0,0,0,.18)"><svg viewBox="0 0 24 24" width="26" height="26" style="stroke:var(--onpri);fill:none;stroke-width:2.4;stroke-linecap:round"><path d="M12 5v14M5 12h14"/></svg></div>{nav('todo')}'''
page(('brand','todo'),body)

# ---------- dry run
code='''<span style="color:var(--mut)">// AI 生成 · 可编辑</span>
<b>const</b> pe = cal.courses.find({{ name: <span style="color:var(--pri);font-weight:600">"体育(3)"</span> }});
cal.courses.update(pe.id, {{
  weekday: <span style="color:var(--pri);font-weight:600">4</span>, periods: [<span style="color:var(--pri);font-weight:600">5</span>, <span style="color:var(--pri);font-weight:600">6</span>]
}});
<b>for</b> (<b>const</b> c <b>of</b> cal.courses.query({{ name: <span style="color:var(--pri);font-weight:600">"实验"</span> }}))
  cal.courses.update(c.id, {{ remind: <span style="color:var(--pri);font-weight:600">30</span> }});'''
mini=''
def mcell(col,a,b,label,kind):
    st={'del':'border:2px dashed var(--del);color:var(--del);background:rgba(229,100,88,.08);text-decoration:line-through','mod':'background:rgba(213,160,59,.18);border:2px solid var(--mod);color:var(--on)','keep':'background:var(--b3);color:var(--f3)'}[kind]
    return f'<div style="position:absolute;left:{36+col*148}px;width:144px;top:{(a-1)*26+2}px;height:{(b-a+1)*26-4}px;border-radius:10px;padding:4px 8px;font-size:11px;font-weight:600;{st}">{label}</div>'
grid=''.join(f'<div style="position:absolute;left:0;top:{i*26}px;font-size:10px;color:var(--mut);width:30px;text-align:right;line-height:26px">{i+1}</div><div style="position:absolute;left:36px;right:0;top:{i*26+25}px;height:1px;background:var(--out)"></div>' for i in range(7))
body=f'''{status()}<div class="bar"><div style="display:flex;align-items:center;gap:6px"><div class="ib" style="background:transparent">{ICON['back']}</div><h1 style="font-size:20px">代码模式</h1></div><div style="display:flex;background:var(--surf);border-radius:14px;padding:3px;font-size:12px"><span style="padding:5px 12px;border-radius:11px;background:var(--pc);color:var(--opc);font-weight:600">AI</span><span style="padding:5px 12px;color:var(--mut)">代码</span></div></div>
<div style="padding:0 16px">
<div style="margin-left:36px;background:var(--pri);color:var(--onpri);border-radius:20px 20px 6px 20px;padding:10px 14px;font-size:13px">把周三的体育课改到周四第 5–6 节，再给所有实验课加上提前 30 分钟提醒</div>
<div style="background:var(--code);border:1px solid var(--out);border-radius:18px;padding:10px 12px;margin-top:10px;font-family:'DejaVu Sans Mono',monospace;font-size:10.5px;line-height:1.5;white-space:pre;color:var(--on)">{code.replace('{{','{').replace('}}','}')}</div>
<div class="card" style="margin-top:10px;padding:12px 14px">
<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px"><b style="font-size:14px">Dry Run 预览</b><span style="font-size:11px;color:var(--add);font-weight:600">✓ 无冲突</span></div>
<div style="display:flex;gap:6px;margin-bottom:10px"><span class="chip" style="background:rgba(70,161,113,.15);color:var(--add)">新增 0</span><span class="chip" style="background:rgba(213,160,59,.18);color:#9A6B12">修改 2</span><span class="chip" style="background:rgba(229,100,88,.14);color:var(--del)">删除 0</span></div>
<div style="display:flex;margin-left:36px;font-size:11px;font-weight:600"><span style="width:148px;text-align:center">周三</span><span style="width:148px;text-align:center">周四</span></div>
<div style="position:relative;height:182px;margin-top:2px">{grid}{mcell(0,3,4,'体育(3)','del')}{mcell(1,3,4,'数字逻辑','keep')}{mcell(1,5,6,'体育(3) → 新位置','mod')}</div>
<div style="font-size:11px;color:var(--mut);margin-top:6px;border-top:1px solid var(--out);padding-top:8px">另有 <b style="color:var(--on)">1 门实验课</b> 提醒改为提前 30 分钟（数据结构实验）</div>
</div>
<div style="display:flex;gap:10px;margin-top:12px"><div style="flex:1;height:48px;border-radius:24px;background:var(--surf);display:grid;place-items:center;font-weight:600;color:var(--mut)">放弃</div><div style="flex:2;height:48px;border-radius:24px;background:var(--pri);color:var(--onpri);display:grid;place-items:center;font-weight:700">应用 2 项变更</div></div>
<div style="text-align:center;font-size:11px;color:var(--mut);margin-top:8px">应用后可在「历史」中一键撤销</div>
</div>{nav('code')}'''
page(('brand','dryrun'),body)

# =================== v2 screens ===================
def row(icon,title,sub='',right='',col='var(--on)',pad=12):
    ic=f'<div style="width:34px;height:34px;border-radius:12px;background:var(--pc);display:grid;place-items:center;flex:none"><div class="ico" style="width:20px;height:20px">{ICON[icon]}</div></div>' if icon else ''
    return f'<div style="display:flex;align-items:center;gap:12px;padding:{pad}px 14px;min-height:46px">{ic}<div style="flex:1"><div style="font-size:14px;font-weight:500;color:{col}">{title}</div>{f"<div style=font-size:11px;color:var(--mut)>{sub}</div>" if sub else ""}</div>{right}</div>'
chev='<div style="width:18px;height:18px">'+ICON['chev']+'</div>'
def crow(c,name,sub,chip=''):
    return f'<div style="display:flex;align-items:center;gap:12px;padding:8px 14px"><div style="width:34px;height:34px;border-radius:12px;background:var(--b{c});flex:none"></div><div style="flex:1"><div style="font-size:14px;font-weight:600">{name}</div><div style="font-size:11px;color:var(--mut)">{sub}</div></div>{chip}</div>'
div='<div style="height:1px;background:var(--out);margin:0 14px"></div>'
def top(title,left='back',right=''):
    return f'<div class="bar"><div style="display:flex;align-items:center;gap:6px"><div class="ib" style="background:transparent">{ICON[left]}</div><h1 style="font-size:20px">{title}</h1></div>{right}</div>'
donebtn=lambda t='完成':f'<div style="height:34px;padding:0 16px;border-radius:17px;background:var(--pri);color:var(--onpri);font-weight:600;font-size:13px;display:grid;place-items:center">{t}</div>'

edit_list=crow(0,'概率论与数理统计','周一 · 周五 · 第 1–2 节')+div+crow(1,'数据结构','周二 · 周三 · 第 1–2 节')+div+crow(1,'数据结构实验','周二 · 第 5 节 · 部分周','<span class="chip" style="background:var(--b3);color:var(--f3)">部分周</span>')+div+crow(4,'体育(3)','周三 · 第 3–4 节')
edit_body=f'''{status()}{top('编辑课表','back',donebtn())}
<div style="padding:0 16px;display:flex;flex-direction:column;gap:12px">
<div style="display:flex;gap:8px"><span class="chip" style="height:28px;font-size:12px">大一下</span><span class="chip" style="height:28px;font-size:12px;background:var(--surf);color:var(--on)">共 20 周</span><span class="chip" style="height:28px;font-size:12px;background:var(--surf);color:var(--on)">每天 12 节</span></div>
<div><div style="display:flex;justify-content:space-between;align-items:center;margin:0 4px 6px"><b style="font-size:13px;color:var(--mut)">课程 · 9</b><span class="chip" style="height:28px;font-size:12px"><span class="ico" style="width:14px;height:14px;display:inline-block">{ICON['plus']}</span>添加课程</span></div>
<div class="card" style="padding:4px 0">{edit_list}{div}<div style="padding:11px 14px;font-size:12px;color:var(--pri);font-weight:600;text-align:center">查看全部 9 门课程</div></div></div>
<div><b style="font-size:13px;color:var(--mut);margin:0 4px 6px;display:block">导入</b><div class="card" style="padding:2px 0">{row('file','从 Excel 导入','课程表.xlsx · 每周一张表','',pad=6)}{div}{row('cal','导入 ICS 日历文件','','',pad=6)}{div}{row('school','从教务系统导入','登录后自动获取','<span class="chip" style="background:var(--out);color:var(--mut)">即将推出</span>',pad=6)}</div></div>
<div class="card" style="padding:2px 0">{row('clock','调整节次时间','每天 12 节','',pad=6)}{div}{row('copy','复制到下一学期','','',pad=6)}</div>
<div style="display:flex;align-items:center;gap:10px;padding:10px 14px;border-radius:18px;border:1.5px dashed var(--out)"><div class="ico" style="width:20px;height:20px;opacity:.6">{ICON['code']}</div><div style="flex:1"><div style="font-size:13px;font-weight:500;color:var(--mut)">高级修改</div><div style="font-size:11px;color:var(--mut);opacity:.85">用一句话或代码批量调整 · 先预览再应用</div></div><div class="ico" style="width:18px;height:18px;opacity:.6">{ICON['chev']}</div></div>
</div>'''
page(('brand','edit_list'),edit_body)

# ---- edit course form
def field(label,val,hint=''): return f'<div class="card" style="padding:9px 14px;margin-bottom:8px"><div style="font-size:10.5px;color:var(--mut)">{label}</div><div style="font-size:15px;font-weight:500">{val}{f"<span style=color:var(--mut);font-size:11px;margin-left:6px>{hint}</span>" if hint else ""}</div></div>'
sw_=''.join(f'<div style="width:30px;height:30px;border-radius:11px;background:var(--b{i});display:grid;place-items:center;{"outline:2.5px solid var(--pri);outline-offset:2px" if i==0 else ""}"></div>' for i in range(8))
wk=''.join(f'<div style="width:36px;height:36px;border-radius:12px;display:grid;place-items:center;font-size:13px;font-weight:600;{"background:var(--pri);color:var(--onpri)" if d in "一五" else "background:var(--bg);color:var(--mut)"}">{d}</div>' for d in '一二三四五六日')
wg=''.join(f'<div style="width:28px;height:26px;border-radius:9px;display:grid;place-items:center;font-size:11px;{"background:var(--pc);color:var(--opc);font-weight:600" if w<=16 else "background:var(--bg);color:var(--mut)"}">{w}</div>' for w in range(1,21))
form=f'''{status()}{top('编辑课程','x',donebtn('保存'))}
<div style="padding:0 16px">{field('课程名称','概率论与数理统计')}
<div style="display:flex;gap:8px"><div style="flex:1">{field('教师','—')}</div><div style="flex:1">{field('教室','教3-204')}</div></div>
<div class="card" style="padding:12px 14px;margin-bottom:8px"><div style="font-size:10.5px;color:var(--mut);margin-bottom:8px">颜色</div><div style="display:flex;gap:9px">{sw_}</div></div>
<div class="card" style="padding:12px 14px;margin-bottom:8px"><div style="font-size:10.5px;color:var(--mut);margin-bottom:8px">上课时间</div><div style="display:flex;gap:6px;margin-bottom:12px">{wk}</div>
<div style="display:flex;align-items:center;gap:10px"><span class="chip" style="height:32px;font-size:13px">第 1 节</span><span style="color:var(--mut)">至</span><span class="chip" style="height:32px;font-size:13px">第 2 节</span><span style="font-size:11px;color:var(--mut)">08:30 – 10:05</span></div></div>
<div class="card" style="padding:12px 14px;margin-bottom:8px"><div style="display:flex;justify-content:space-between;margin-bottom:8px"><span style="font-size:10.5px;color:var(--mut)">周次</span><span style="display:flex;gap:6px"><span class="chip" style="background:var(--bg);color:var(--mut)">单周</span><span class="chip" style="background:var(--bg);color:var(--mut)">双周</span><span class="chip">全部</span></span></div><div style="display:flex;flex-wrap:wrap;gap:4px">{wg}</div></div>
<div class="card" style="padding:0 0;margin-bottom:8px">{row('','提醒','提前 15 分钟','')}{div}{row('','关联待办','添加作业 / 复习任务','')}</div>
<div style="text-align:center;color:var(--del);font-size:13px;font-weight:600;padding:8px">删除这门课</div></div>'''
page(('brand','edit_course'),form)

# ---- Advanced edit A: bottom sheet
def chg(kind,title,before,after):
    col={'mod':'var(--mod)','add':'var(--add)','del':'var(--del)'}[kind]; lab={'mod':'修改','add':'新增','del':'删除'}[kind]
    return f'<div style="display:flex;gap:10px;padding:10px 12px;border-radius:16px;background:var(--bg)"><div style="width:4px;border-radius:2px;background:{col}"></div><div style="flex:1"><div style="display:flex;justify-content:space-between"><b style="font-size:13px">{title}</b><span style="font-size:10.5px;color:{col};font-weight:700">{lab}</span></div><div style="font-size:12px;margin-top:3px"><span style="color:var(--mut);text-decoration:line-through">{before}</span> <span style="color:var(--mut)">→</span> <b>{after}</b></div></div></div>'
bg_cards=''.join(f'<div class="card" style="height:56px;margin-bottom:10px"></div>' for _ in range(6))
sheetA=f'''{status()}{top('编辑课表','back',donebtn())}<div style="padding:0 16px">{bg_cards}</div>
<div style="position:absolute;inset:0;background:rgba(20,30,28,.38)"></div>
<div style="position:absolute;left:0;right:0;bottom:0;top:362px;background:var(--surf);border-radius:30px 30px 0 0;padding:10px 20px 0">
<div style="width:40px;height:4px;border-radius:2px;background:var(--out);margin:0 auto 14px"></div>
<div style="display:flex;justify-content:space-between;align-items:center"><div style="display:flex;align-items:center;gap:8px"><div class="ico" style="width:22px;height:22px">{ICON['spark']}</div><b style="font-size:18px">高级修改</b></div><span class="chip" style="background:var(--sc);color:var(--osc)">先预览，再应用</span></div>
<div style="margin-top:14px;border-radius:20px;background:var(--bg);padding:12px 14px;font-size:14px;line-height:1.5;min-height:84px">把周三的体育课改到周四第 5–6 节，再给所有实验课加上提前 30 分钟提醒<span style="display:inline-block;width:2px;height:16px;background:var(--pri);vertical-align:-3px;margin-left:2px"></span></div>
<div style="display:flex;gap:6px;margin-top:8px"><span class="chip" style="background:var(--bg);color:var(--mut)">批量改教室</span><span class="chip" style="background:var(--bg);color:var(--mut)">删除第 8 周后的课</span><span class="chip" style="background:var(--bg);color:var(--mut)">+</span></div>
<div style="display:flex;justify-content:space-between;align-items:center;margin:16px 2px 8px"><b style="font-size:13px">将进行 2 项变更</b><span style="font-size:11px;color:var(--add);font-weight:600">✓ 无冲突</span></div>
<div style="display:flex;flex-direction:column;gap:8px">{chg('mod','体育(3)','周三 第 3–4 节','周四 第 5–6 节')}{chg('mod','数据结构实验','无提醒','提前 30 分钟')}</div>
<div style="display:flex;gap:10px;margin-top:16px"><div style="flex:1;height:48px;border-radius:24px;background:var(--pc);color:var(--opc);display:grid;place-items:center;font-weight:600;font-size:14px">在课表上预览</div><div style="flex:1;height:48px;border-radius:24px;background:var(--pri);color:var(--onpri);display:grid;place-items:center;font-weight:700;font-size:14px">应用变更</div></div>
<div style="text-align:center;margin-top:14px;font-size:12px;color:var(--mut)">&lt;/&gt; 查看 / 编辑代码</div></div>'''
page(('brand','adv_a'),sheetA)

# ---- Advanced edit B: full page with code
codeB='''<div style="font-family:'DejaVu Sans Mono',monospace;font-size:11.5px;line-height:1.75;color:#DCE8E4;white-space:pre">'''
lines=[
 '<span style="color:#7F9A93">// 把周三体育课挪到周四 5–6 节</span>',
 '<span style="color:#B7A8F0">const</span> pe = cal.courses',
 '  .find({ name: <span style="color:#F4B393">"体育(3)"</span> });',
 '',
 'cal.courses.update(pe.id, {',
 '  weekday: <span style="color:#8FD6C3">4</span>,',
 '  periods: [<span style="color:#8FD6C3">5</span>, <span style="color:#8FD6C3">6</span>],',
 '});',
 '',
 '<span style="color:#B7A8F0">for</span> (<span style="color:#B7A8F0">const</span> c <span style="color:#B7A8F0">of</span> cal.courses',
 '  .query({ name: <span style="color:#F4B393">"实验"</span> }))',
 '  cal.courses.update(c.id, { remind: <span style="color:#8FD6C3">30</span> });']
ed=''.join(f'<div style="display:flex"><span style="width:26px;color:#5E766F;text-align:right;margin-right:12px;user-select:none">{i+1}</span><span>{l}</span></div>' for i,l in enumerate(lines))
seg=lambda items,act:'<div style="display:flex;background:var(--surf);border-radius:18px;padding:4px;margin:0 16px 12px">'+''.join(f'<div style="flex:1;text-align:center;padding:8px 0;border-radius:14px;font-size:13px;{"background:var(--pc);color:var(--opc);font-weight:700" if i==act else "color:var(--mut)"}">{t}</div>' for i,t in enumerate(items))+'</div>'
pageB=f'''{status()}{top('高级修改','back','<span class="chip" style="background:var(--surf);color:var(--mut)">JavaScript</span>')}{seg(['描述','代码','预览'],1)}
<div style="margin:0 16px;border-radius:22px;background:#1C2926;padding:14px 12px 12px">{codeB}{ed}</div></div>
<div style="display:flex;align-items:center;gap:8px;margin:10px 20px 0;font-size:11px;color:var(--mut)"><div class="ico" style="width:16px;height:16px">{ICON['spark']}</div>由 AI 生成，可直接修改。代码只能读写日程，不能联网。</div>
<div class="card" style="margin:12px 16px 0;padding:12px 14px"><div style="display:flex;justify-content:space-between"><b style="font-size:13px">试运行结果</b><span style="font-size:11px;color:var(--add);font-weight:600">✓ 0.04s · 无错误</span></div><div style="font-size:12px;color:var(--mut);margin-top:4px">将修改 2 项 · 新增 0 · 删除 0</div></div>
<div style="display:flex;gap:10px;margin:14px 16px 0"><div style="flex:1;height:48px;border-radius:24px;background:var(--surf);display:grid;place-items:center;font-weight:600;font-size:14px">试运行</div><div style="flex:1.2;height:48px;border-radius:24px;background:var(--pri);color:var(--onpri);display:grid;place-items:center;font-weight:700;font-size:14px">预览并应用</div></div>'''
page(('brand','adv_b'),pageB)

# ---- appearance
presets=[('薄荷','#4FA796','#CDEBE3','#FBE3D6',1),('蜜桃','#E8896B','#FBE0D5','#E6EFD9',0),('薰衣草','#7A6CC4','#E3DEFA','#F6DDE8',0),('天空','#4A8FD0','#D6E7F8','#FBEFCF',0),('玫瑰','#D5627F','#F8DCE4','#DCEFE6',0),('柠檬','#B9951C','#F5EBC2','#DDE7F6',0)]
pg=''.join(f'<div style="width:calc(33.3% - 7px);border-radius:18px;background:var(--surf);padding:10px 8px 8px;text-align:center;{"outline:2px solid var(--pri);" if sel else ""}"><div style="display:flex;justify-content:center;margin-bottom:6px"><i style="width:26px;height:26px;border-radius:13px;background:{a};display:block"></i><i style="width:26px;height:26px;border-radius:13px;background:{b};display:block;margin-left:-8px"></i><i style="width:26px;height:26px;border-radius:13px;background:{c};display:block;margin-left:-8px"></i></div><div style="font-size:12px;font-weight:{600 if sel else 500}">{n}</div></div>' for n,a,b,c,sel in presets)
mini=''.join(f'<div style="flex:1;height:{h}px;border-radius:10px;background:var(--b{i});align-self:flex-end"></div>' for i,h in [(0,58),(1,78),(3,44),(2,66),(4,52)])
wall=''.join(f'<i style="width:36px;height:36px;border-radius:12px;background:{c};display:block"></i>' for c in ['#7A6CC4','#E3DEFA','#F6DDE8','#D8EAF6','#F6F3FA'])
appear=f'''{status()}{top('外观')}
<div style="padding:0 16px">
<div class="card" style="padding:14px;margin-bottom:12px"><div style="display:flex;gap:8px;height:80px;padding:0 4px">{mini}</div><div style="font-size:11px;color:var(--mut);margin-top:8px;text-align:center">实时预览</div></div>
<b style="font-size:13px;color:var(--mut);margin:0 4px 6px;display:block">主题色</b>
<div class="card" style="padding:14px;margin-bottom:10px;outline:2px solid var(--pri)"><div style="display:flex;align-items:center;justify-content:space-between"><div><div style="font-size:15px;font-weight:700">动态取色 <span class="chip" style="margin-left:4px">推荐</span></div><div style="font-size:11px;color:var(--mut);margin-top:2px">跟随系统壁纸（Android 12+）</div></div><div style="width:46px;height:28px;border-radius:14px;background:var(--pri);position:relative"><i style="position:absolute;right:3px;top:3px;width:22px;height:22px;border-radius:11px;background:#fff;display:block"></i></div></div><div style="display:flex;gap:8px;margin-top:12px">{wall}</div></div>
<div style="font-size:11px;color:var(--mut);margin:0 4px 8px">或选择预设主题（选择后将关闭动态取色）</div>
<div style="display:flex;flex-wrap:wrap;gap:10px;margin-bottom:12px">{pg}</div>
<div class="card" style="padding:2px 0;margin-bottom:12px">{row('spark','自定义种子色','选一个颜色，自动生成整套配色','',pad=8)}</div>
<b style="font-size:13px;color:var(--mut);margin:0 4px 6px;display:block">深色模式</b>
{seg(['跟随系统','浅色','深色'],0).replace('margin:0 16px 12px','margin:0')}
</div>'''
page(('brand','appearance'),appear)

# ---- parcel placeholder
ICON['box']='<svg viewBox="0 0 24 24"><path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z"/><path d="M4 7.5l8 4.5 8-4.5M12 12v9"/></svg>'
ICON['bell']='<svg viewBox="0 0 24 24"><path d="M6 17V11a6 6 0 0 1 12 0v6l1.5 2h-15z"/><path d="M10 21h4"/></svg>'
tog=lambda on:f'<div style="width:46px;height:28px;border-radius:14px;background:{"var(--pri)" if on else "var(--out)"};position:relative;flex:none"><i style="position:absolute;top:3px;{"right" if on else "left"}:3px;width:22px;height:22px;border-radius:11px;background:#fff"></i></div>'
step=lambda n,t,s:f'<div style="display:flex;gap:12px;padding:8px 14px"><div style="width:24px;height:24px;border-radius:12px;background:var(--pc);color:var(--opc);display:grid;place-items:center;font-size:12px;font-weight:700;flex:none">{n}</div><div><div style="font-size:13.5px;font-weight:600">{t}</div><div style="font-size:11px;color:var(--mut);margin-top:1px">{s}</div></div></div>'
parcel=f'''{status()}{top('快递提醒','back','<span class="chip" style="background:var(--b3);color:var(--f3)">可选 · 即将推出</span>')}
<div style="padding:0 16px;display:flex;flex-direction:column;gap:12px">
<div class="card" style="padding:14px;display:flex;align-items:center;gap:12px"><div style="width:42px;height:42px;border-radius:14px;background:var(--pc);display:grid;place-items:center"><div class="ico" style="width:24px;height:24px">{ICON['box']}</div></div><div style="flex:1"><div style="font-size:15px;font-weight:700">自动识别取件通知</div><div style="font-size:11px;color:var(--mut);margin-top:2px">检测到取件短信/App 通知后，自动加入待办</div></div>{tog(0)}</div>
<div><b style="font-size:13px;color:var(--mut);margin:0 4px 6px;display:block">识别效果预览</b>
<div class="card" style="padding:12px 14px"><div style="display:flex;gap:8px;align-items:center;font-size:11px;color:var(--mut)"><div class="ico" style="width:14px;height:14px">{ICON['bell']}</div>菜鸟驿站 · 刚刚</div><div style="font-size:13px;margin-top:4px;line-height:1.5">【菜鸟驿站】取件码 <b>3-2-0817</b>，包裹已到 <b>南门快递站</b>，请今晚 21:00 前领取。</div>
<div style="text-align:center;color:var(--mut);font-size:16px;margin:4px 0">↓</div>
<div style="display:flex;gap:10px;padding:10px 12px;border-radius:16px;background:var(--bg)"><div style="width:4px;border-radius:2px;background:var(--add)"></div><div style="flex:1"><div style="display:flex;justify-content:space-between"><b style="font-size:13px">取快递 · 南门快递站</b><span style="font-size:10.5px;color:var(--add);font-weight:700">新增</span></div><div style="font-size:12px;color:var(--mut);margin-top:3px">取件码 3-2-0817 · 今天 21:00 前 · 预计 10 分钟</div></div></div></div></div>
<div><b style="font-size:13px;color:var(--mut);margin:0 4px 6px;display:block">将会这样工作</b><div class="card" style="padding:4px 0">{step(1,'你授权通知读取','仅在本机处理，默认不上传、不联网')}{div}{step(2,'本地规则识别','取件码、地点、截止时间；识别不了再询问是否交给 AI')}{div}{step(3,'先确认，再加入','默认弹出确认卡片；可改为自动加入并避开课程时间')}</div></div>
<div class="card" style="padding:2px 0;opacity:.55">{row('bell','通知读取权限','尚未授权','',pad=6)}{div}{row('clock','默认提醒时间','截止前 1 小时','',pad=6)}</div>
</div>'''
page(('brand','parcel'),parcel)
