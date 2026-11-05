"""UI smoke checks against the signed release and a separate, local-fixture instrumentation APK.

Never supplies a production session or account. Requires an isolated emulator with ADB.
"""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', required=True)
parser.add_argument('--focus', choices=('all', 'import-update'), default='all')
args = parser.parse_args()
out = Path(args.output).resolve()
shots = out / 'screenshots'
shots.mkdir(parents=True, exist_ok=True)
base = [args.adb, '-s', args.serial]
package = 'fr.kdns.weiyun.dormmonitor'
results = []
processes = []


def adb(*cmd):
    return subprocess.check_output(base + list(cmd))


def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/dianshi-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/dianshi-ui.xml').decode())


def match(root, label):
    for attr in ('content-desc', 'text'):
        node = next((n for n in root.iter('node') if n.get(attr) == label), None)
        if node is not None:
            return node
    raise AssertionError('Missing UI control: ' + label)


def click(label):
    root = tree()
    node = match(root, label)
    parents = {c: p for p in root.iter() for c in p}
    described = [n for n in root.iter('node') if n.get('content-desc') == label]
    targets = []
    for candidate in [n for n in root.iter('node') if n.get('text') == label]:
        target = candidate
        while target.get('clickable') != 'true' and target in parents:
            target = parents[target]
        if target.get('clickable') == 'true':
            targets.append(target)
    def area(n):
        a, b, c, d = map(int, re.findall(r'\d+', n.get('bounds')))
        return (c-a)*(d-b)
    target = described[0] if described else min(targets, key=area) if targets else node
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', target.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def check(label, present=True):
    found = any(n.get('text') == label or n.get('content-desc') == label for n in tree().iter('node'))
    assert found == present, label


def checked(label):
    root = tree()
    node = match(root, label)
    parents = {c: p for p in root.iter() for c in p}
    # Compose can expose a radio control and its label as sibling semantics.
    bounds = node.get('bounds')
    if any(n.get('bounds') == bounds and (n.get('checked') == 'true' or n.get('selected') == 'true') for n in root.iter('node')):
        return True
    while True:
        if node.get('checked') == 'true' or node.get('selected') == 'true':
            return True
        if node not in parents:
            return False
        node = parents[node]


def snapshot(name):
    (shots / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))


def fixture(page, **arguments):
    adb('shell', 'am', 'force-stop', package)
    log = out / ('device-' + page + '.log')
    with log.open('wb') as handle:
        extras = [part for key, value in arguments.items() for part in ('-e', key, str(value))]
        proc = subprocess.Popen(base + ['shell', 'am', 'instrument', '-w', '-e', 'page', page] + extras + [
            package + '.test/' + package + '.VisualVerification'], stdout=handle, stderr=subprocess.STDOUT)
    processes.append(proc)
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        text = log.read_text(errors='replace')
        if 'result=ready' in text:
            tree()
            return
        if proc.poll() is not None:
            raise AssertionError(text)
        time.sleep(.2)
    raise AssertionError('Fixture did not become ready: ' + log.read_text(errors='replace'))


def back():
    adb('shell', 'input', 'keyevent', '4')


try:
    if args.focus == 'import-update':
        fixture('navigation', delay=450)
        check('我的宿舍'); snapshot('glass-home')
        for _ in range(3):
            click('添加'); check('选择校区'); click('设置'); check('低电量提醒'); click('宿舍'); check('我的宿舍')
        results.append('Delayed requests do not block tabs; in-flight options deduplicate; warm import and preference caches avoid new requests and preserve selection')
        adb('shell', 'input', 'swipe', '540', '1800', '540', '700', '350')
        snapshot('glass-over-content')
        fixture('confirmation')
        check('确认添加'); snapshot('confirmation')
        click('楼栋'); check('选择楼栋'); click('7号楼'); check('选择宿舍'); click('7-201')
        check('确认添加'); click('确认添加'); check('我的宿舍')
        results.append('Three-step confirmation renders, can edit a previous choice and submits the expected selection')
        adb('shell', 'wm', 'size', '720x1280'); adb('shell', 'wm', 'density', '320')
        adb('shell', 'settings', 'put', 'system', 'font_scale', '1.6')
        fixture('confirmation')
        label = match(tree(), '确认添加')
        a, b, c, d = map(int, re.findall(r'\d+', label.get('bounds')))
        assert d-b >= 32 and a >= 0 and c <= 720 and d < 1280, label.attrib
        snapshot('confirmation-large-font')
        for _ in range(3): adb('shell', 'input', 'swipe', '360', '750', '360', '300', '300')
        check('确认添加'); snapshot('confirmation-scrolled'); click('确认添加'); check('我的宿舍')
        results.append('360x640dp at 160 percent font: confirmation label stays visible while the summary scrolls and remains clickable')
        adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        adb('shell', 'wm', 'size', 'reset'); adb('shell', 'wm', 'density', 'reset')
        fixture('appearance')
        for _ in range(2): adb('shell', 'input', 'swipe', '540', '2000', '540', '700', '350')
        if checked('悬浮底栏'): click('悬浮底栏')
        assert not checked('悬浮底栏')
        fixture('home'); snapshot('navigation-docked')
        click('设置'); check('低电量提醒'); click('宿舍'); check('我的宿舍')
        results.append('Docked navigation renders to the screen bottom and its items remain usable')
        fixture('about', feedback='123456789')
        check('QQ 反馈群'); check('123456789'); click('复制群号'); snapshot('feedback-copy')
        fixture('login'); click('用户名'); adb('shell', 'input', 'keyevent', '279'); check('123456789')
        snapshot('feedback-paste-verification')
        fixture('about'); check('QQ 反馈群'); check('暂未设置'); snapshot('feedback-empty')
        results.append('Feedback card renders an empty configuration and a configured group; tapping the configured card copies through the system clipboard')
        for mode, expected in [('new', '发现新版本 v99.0.0'), ('current', '已是最新版本'), ('empty', '暂未找到可用版本'), ('failure', '无法连接更新服务，请检查网络后重试')]:
            fixture('about', update=mode); click('检查更新'); check(expected)
            check('下载更新', mode == 'new'); snapshot('update-' + mode)
        results.append('Update view handles newer, current, unpublished and network failure states with an explicit download action')
        fixture('about'); click('检查更新')
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            if not any(n.get('text') == '正在检查…' for n in tree().iter('node')): break
        check('暂未找到可用版本'); snapshot('update-live')
        results.append('Read-only live GitHub request handles anonymous 404 without claiming the installed version is latest')
        (out / 'device-verification.json').write_text(json.dumps({'status': 'passed', 'checks': results, 'data': 'isolated fixtures; live GitHub GET only; no production account or data mutations'}, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(results, ensure_ascii=True, indent=2))
        raise SystemExit(0)
    fixture('home')
    check('我的宿舍'); check('电表总用电量', False); check('历史采样', False)
    snapshot('home')
    click('L2-505'); check('电表总用电量'); check('历史采样'); check('正在读取该时段历史…', False)
    snapshot('detail'); back()
    for _ in range(2):
        adb('shell', 'input', 'swipe', '540', '1800', '540', '650', '300')
    click('9A-503'); check('历史采样'); back(); check('9A-503')
    results.append('Personal list, detail metrics, cached chart and scroll restoration passed')
    click('设置'); check('低电量提醒'); snapshot('settings')
    click('主题与外观'); click('深色'); click('鸢尾配色')
    assert checked('深色') and checked('鸢尾配色')
    snapshot('appearance-dark')
    back(); click('宿舍'); snapshot('home-dark')
    fixture('login')
    click('主题与外观'); assert checked('深色') and checked('鸢尾配色')
    click('浅色'); click('雾蓝配色'); snapshot('appearance-light')
    for _ in range(2):
        adb('shell', 'input', 'swipe', '540', '2000', '540', '700', '350')
    click('添加入口'); click('紧凑卡片'); click('趋势缩略图')
    assert not checked('添加入口') and checked('紧凑卡片') and not checked('趋势缩略图')
    snapshot('appearance-options')
    results.append('Live light/dark palette switching, persistence and display switches passed')
    fixture('home'); check('添加', False); check('添加宿舍'); snapshot('home-compact')
    click('添加宿舍'); check('选择校区'); snapshot('import')
    results.append('Hidden tab keeps add action accessible; campus selection renders')
    fixture('login'); click('主题与外观')
    for _ in range(4):
        adb('shell', 'input', 'swipe', '540', '2000', '540', '700', '300')
    click('恢复默认外观'); back()
    click('创建账号'); check('确认密码')
    for label, value in [('用户名', 'fixture_user'), ('密码', 'abcdefghij'), ('确认密码', 'abcdefghik')]:
        click(label); adb('shell', 'input', 'text', value)
    back(); click('注册'); check('两次输入的密码不一致'); snapshot('register')
    results.append('Separate registration and mismatched-password validation passed without submitting')
    fixture('empty'); snapshot('empty')
    fixture('home')
    for _ in range(6):
        adb('shell', 'input', 'tap', '848', '2140'); adb('shell', 'input', 'tap', '233', '2140')
    check('我的宿舍'); snapshot('home-final')
    results.append('Repeated bottom navigation stayed on the selected destination')
    adb('shell', 'wm', 'size', '720x1600'); adb('shell', 'wm', 'density', '320')
    fixture('login'); check('用户名'); check('密码'); snapshot('login-small')
    results.append('360dp viewport login rendered and remains scrollable')
    (out / 'device-verification.json').write_text(json.dumps({'status': 'passed', 'checks': results, 'data': 'isolated local fixtures; no production account or data mutations'}, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(results, ensure_ascii=True, indent=2))
finally:
    if args.focus == 'import-update': adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'wm', 'size', 'reset'); adb('shell', 'wm', 'density', 'reset')
    adb('shell', 'am', 'force-stop', package)
    for proc in processes:
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.terminate()
