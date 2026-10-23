#!/bin/bash
for f in "$@"; do chromium --headless --no-sandbox --disable-gpu --hide-scrollbars --force-device-scale-factor=2 --window-size=390,844 --screenshot=/data/m0/$f.png file:///data/m0/$f.html >/dev/null 2>&1; done
