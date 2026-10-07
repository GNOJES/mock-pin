"""Verify a freshly installed release starts and survives on one connected device."""
import os
import subprocess
import time

adb = os.environ.get('ADB', os.path.expanduser('~/Library/Android/sdk/platform-tools/adb'))
package = 'com.gnojes.mockpin'

def run(*args):
    return subprocess.run([adb, *args], capture_output=True, text=True, check=False)

run('shell', 'am', 'force-stop', package)
result = run('shell', 'am', 'start', '-W', '-n', package + '/.MainActivity')
assert result.returncode == 0 and 'Status: ok' in result.stdout, 'Activity failed to launch'
for _ in range(16):
    time.sleep(0.5)
    assert run('shell', 'pidof', package).stdout.strip(), 'App process exited after launch'
window = run('shell', 'dumpsys', 'window').stdout
assert any(package in line for line in window.splitlines() if 'mCurrentFocus=' in line), 'App is not in foreground'
print('PASS: app survived cold launch and remains in foreground')
