(function() {
    'use strict';

    // 避免重复注入
    if (window.__tileTvSpatialNav) return;
    window.__tileTvSpatialNav = true;

    // 可聚焦元素选择器
    var FOCUSABLE = 'a[href], button, input, select, textarea, [tabindex], [onclick], [role="button"], [role="link"], [role="tab"], [role="menuitem"], video, [contenteditable="true"]';
    var HIGHLIGHT_CLASS = 'tiletv-focus';

    // 注入高亮样式
    var style = document.createElement('style');
    style.textContent = '.' + HIGHLIGHT_CLASS + '{outline:3px solid #FF6B35!important;outline-offset:2px!important;box-shadow:0 0 15px rgba(255,107,53,0.7)!important;}';
    document.head.appendChild(style);

    var currentFocus = null;

    // 获取所有可见的可聚焦元素
    function getVisibleFocusables() {
        var all = document.querySelectorAll(FOCUSABLE);
        var result = [];
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            var rect = el.getBoundingClientRect();
            // 可见且有尺寸
            if (rect.width > 5 && rect.height > 5 &&
                rect.bottom > 0 && rect.top < window.innerHeight &&
                rect.right > 0 && rect.left < window.innerWidth) {
                var cs = window.getComputedStyle(el);
                if (cs.visibility !== 'hidden' && cs.display !== 'none' && cs.opacity !== '0') {
                    result.push(el);
                }
            }
        }
        return result;
    }

    // 元素中心坐标
    function center(rect) {
        return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
    }

    // 在指定方向上找到最佳候选元素
    function findBest(direction) {
        var focusables = getVisibleFocusables();
        if (focusables.length === 0) return null;
        if (!currentFocus || !document.body.contains(currentFocus)) return focusables[0];

        var cRect = currentFocus.getBoundingClientRect();
        var cCenter = center(cRect);

        var best = null;
        var bestScore = Infinity;

        for (var i = 0; i < focusables.length; i++) {
            var el = focusables[i];
            if (el === currentFocus) continue;

            var rect = el.getBoundingClientRect();
            var eCenter = center(rect);

            var dx = eCenter.x - cCenter.x;
            var dy = eCenter.y - cCenter.y;

            // 方向过滤
            var ok = false;
            switch (direction) {
                case 'up':    ok = dy < -5; break;
                case 'down':  ok = dy > 5; break;
                case 'left':  ok = dx < -5; break;
                case 'right': ok = dx > 5; break;
            }
            if (!ok) continue;

            // 计算得分：主轴距离 + 侧轴距离惩罚
            var primary, secondary;
            if (direction === 'up' || direction === 'down') {
                primary = Math.abs(dy);
                secondary = Math.abs(dx);
            } else {
                primary = Math.abs(dx);
                secondary = Math.abs(dy);
            }

            var score = primary + secondary * 3;

            if (score < bestScore) {
                bestScore = score;
                best = el;
            }
        }

        return best;
    }

    // 设置焦点到指定元素
    function setFocus(el) {
        if (currentFocus) {
            currentFocus.classList.remove(HIGHLIGHT_CLASS);
        }
        currentFocus = el;
        if (el) {
            el.classList.add(HIGHLIGHT_CLASS);
            el.focus();
            // 滚动到可见区域
            try {
                el.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
            } catch (e) {
                el.scrollIntoView(false);
            }
        }
    }

    // 键盘事件处理
    function onKeyDown(e) {
        var direction = null;
        var keyCode = e.keyCode || e.which;

        switch (keyCode) {
            case 38: direction = 'up'; break;
            case 40: direction = 'down'; break;
            case 37: direction = 'left'; break;
            case 39: direction = 'right'; break;
            case 13: // Enter / OK - 触发点击
                if (currentFocus) {
                    currentFocus.click();
                }
                return;
            default:
                return;
        }

        e.preventDefault();
        e.stopPropagation();

        var next = findBest(direction);
        if (next) {
            setFocus(next);
        } else if (direction === 'down') {
            // 没找到下方元素，尝试滚动页面
            window.scrollBy(0, 200);
            // 滚动后重新查找
            setTimeout(function() {
                var retry = findBest(direction);
                if (retry) setFocus(retry);
            }, 300);
        } else if (direction === 'up') {
            window.scrollBy(0, -200);
            setTimeout(function() {
                var retry = findBest(direction);
                if (retry) setFocus(retry);
            }, 300);
        }
    }

    // 注册事件监听（捕获阶段，优先于页面自身的处理）
    document.addEventListener('keydown', onKeyDown, true);

    // 页面加载后自动聚焦第一个元素
    setTimeout(function() {
        var focusables = getVisibleFocusables();
        if (focusables.length > 0 && !currentFocus) {
            setFocus(focusables[0]);
        }
    }, 800);

    // 监听 DOM 变化，如果当前焦点元素被移除则重新聚焦
    if (typeof MutationObserver !== 'undefined') {
        var observer = new MutationObserver(function() {
            if (currentFocus && !document.body.contains(currentFocus)) {
                var focusables = getVisibleFocusables();
                if (focusables.length > 0) {
                    setFocus(focusables[0]);
                } else {
                    currentFocus = null;
                }
            }
        });
        observer.observe(document.body, { childList: true, subtree: true });
    }

    console.log('[TileTV] Spatial navigation initialized, found ' + getVisibleFocusables().length + ' focusable elements');
})();
