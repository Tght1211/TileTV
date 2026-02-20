(function () {
  'use strict';

  // ========== Configuration ==========
  var wsUrl = 'ws://' + location.hostname + ':' + (location.port || '9870');

  // ========== DOM References ==========
  var statusDot = document.querySelector('.status-dot');
  var statusText = document.querySelector('.status-text');
  var previewImage = document.getElementById('previewImage');
  var previewPlaceholder = document.getElementById('previewPlaceholder');
  var currentUrl = document.getElementById('currentUrl');
  var aiStatus = document.getElementById('aiStatus');
  var aiText = document.getElementById('aiText');
  var voiceBtn = document.getElementById('voiceBtn');
  var voiceRing = document.getElementById('voiceRing');
  var voiceHint = document.getElementById('voiceHint');
  var textInput = document.getElementById('textInput');
  var sendBtn = document.getElementById('sendBtn');

  // ========== State ==========
  var ws = null;
  var isRecording = false;
  var recognition = null;
  var reconnectTimer = null;
  var reconnectDelay = 3000;

  // ========== WebSocket ==========
  function connect() {
    if (ws && (ws.readyState === WebSocket.CONNECTING || ws.readyState === WebSocket.OPEN)) {
      return;
    }

    try {
      ws = new WebSocket(wsUrl);
    } catch (e) {
      statusDot.classList.remove('connected');
      statusText.textContent = '连接失败';
      scheduleReconnect();
      return;
    }

    ws.onopen = function () {
      statusDot.classList.add('connected');
      statusText.textContent = '已连接';
      reconnectDelay = 3000; // reset backoff
      // Request current state
      safeSend({ type: 'ping' });
    };

    ws.onmessage = function (event) {
      try {
        var msg = JSON.parse(event.data);
        handleMessage(msg);
      } catch (e) {
        // ignore malformed messages
      }
    };

    ws.onclose = function () {
      statusDot.classList.remove('connected');
      statusText.textContent = '已断开 · 重连中...';
      scheduleReconnect();
    };

    ws.onerror = function () {
      statusDot.classList.remove('connected');
      statusText.textContent = '连接失败';
    };
  }

  function scheduleReconnect() {
    if (reconnectTimer) return;
    reconnectTimer = setTimeout(function () {
      reconnectTimer = null;
      connect();
    }, reconnectDelay);
    // Exponential backoff capped at 30s
    reconnectDelay = Math.min(reconnectDelay * 1.5, 30000);
  }

  function safeSend(obj) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(obj));
    }
  }

  // ========== Message Handler ==========
  function handleMessage(msg) {
    switch (msg.type) {
      case 'frame':
        // Display screenshot
        previewImage.src = 'data:image/jpeg;base64,' + msg.data;
        previewImage.style.display = 'block';
        previewPlaceholder.style.display = 'none';
        break;

      case 'status':
        updateAiStatus(msg.text, msg.level || 'info');
        break;

      case 'toast':
        showToast(msg.text);
        break;

      case 'pong':
        currentUrl.textContent = msg.title || msg.url || '空白页';
        if (msg.frame) {
          previewImage.src = 'data:image/jpeg;base64,' + msg.frame;
          previewImage.style.display = 'block';
          previewPlaceholder.style.display = 'none';
        }
        break;

      case 'url':
        currentUrl.textContent = msg.title || msg.url || '空白页';
        break;

      default:
        break;
    }
  }

  // ========== AI Status ==========
  function updateAiStatus(text, level) {
    aiText.textContent = text;
    // Reset classes, keep base
    aiStatus.className = 'ai-status glass';
    if (level) {
      aiStatus.classList.add(level);
    }
    // Spin icon when thinking
    var icon = aiStatus.querySelector('.ai-icon');
    if (level === 'thinking') {
      icon.classList.add('spinning');
    } else {
      icon.classList.remove('spinning');
    }
  }

  // ========== Send Command ==========
  function sendCommand(text) {
    if (!text || !text.trim()) return;
    text = text.trim();
    safeSend({ type: 'voice', text: text });
    updateAiStatus('发送: "' + text + '"', 'thinking');
  }

  // ========== Speech Recognition ==========
  function initSpeechRecognition() {
    var SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      voiceHint.textContent = '浏览器不支持语音识别';
      voiceBtn.style.opacity = '0.4';
      voiceBtn.style.pointerEvents = 'none';
      return;
    }

    recognition = new SpeechRecognition();
    recognition.lang = 'zh-CN';
    recognition.continuous = false;
    recognition.interimResults = true;
    recognition.maxAlternatives = 1;

    recognition.onresult = function (event) {
      var transcript = event.results[0][0].transcript;
      if (event.results[0].isFinal) {
        sendCommand(transcript);
        voiceHint.textContent = '已发送: ' + transcript;
        setTimeout(function () {
          if (!isRecording) {
            voiceHint.textContent = '长按说话';
          }
        }, 3000);
      } else {
        voiceHint.textContent = '识别中: ' + transcript;
      }
    };

    recognition.onerror = function (event) {
      stopRecording();
      if (event.error === 'no-speech') {
        voiceHint.textContent = '未检测到语音，请重试';
      } else if (event.error === 'not-allowed') {
        voiceHint.textContent = '请允许麦克风权限';
      } else {
        voiceHint.textContent = '识别失败，请重试';
      }
    };

    recognition.onend = function () {
      stopRecording();
    };
  }

  function startRecording() {
    if (!recognition || isRecording) return;
    isRecording = true;
    voiceBtn.classList.add('recording');
    voiceRing.classList.add('active');
    voiceHint.textContent = '正在听...';

    try {
      recognition.start();
    } catch (e) {
      // already started
      stopRecording();
      return;
    }

    // Haptic feedback
    if (navigator.vibrate) {
      navigator.vibrate(50);
    }
  }

  function stopRecording() {
    isRecording = false;
    voiceBtn.classList.remove('recording');
    voiceRing.classList.remove('active');
    if (recognition) {
      try {
        recognition.stop();
      } catch (e) {
        // ignore
      }
    }
  }

  // ========== Event Bindings ==========

  // --- Voice button: long press ---
  var pressTimer = null;

  voiceBtn.addEventListener('touchstart', function (e) {
    e.preventDefault();
    pressTimer = setTimeout(function () {
      startRecording();
    }, 200);
  }, { passive: false });

  voiceBtn.addEventListener('touchend', function (e) {
    e.preventDefault();
    clearTimeout(pressTimer);
    pressTimer = null;
    if (isRecording) stopRecording();
  }, { passive: false });

  voiceBtn.addEventListener('touchcancel', function () {
    clearTimeout(pressTimer);
    pressTimer = null;
    if (isRecording) stopRecording();
  });

  // Mouse fallback for desktop testing
  voiceBtn.addEventListener('mousedown', function (e) {
    // skip if touch device already handled
    if ('ontouchstart' in window) return;
    pressTimer = setTimeout(function () {
      startRecording();
    }, 200);
  });

  voiceBtn.addEventListener('mouseup', function () {
    if ('ontouchstart' in window) return;
    clearTimeout(pressTimer);
    pressTimer = null;
    if (isRecording) stopRecording();
  });

  voiceBtn.addEventListener('mouseleave', function () {
    if ('ontouchstart' in window) return;
    clearTimeout(pressTimer);
    pressTimer = null;
    if (isRecording) stopRecording();
  });

  // --- Text input ---
  sendBtn.addEventListener('click', function () {
    var text = textInput.value.trim();
    if (text) {
      sendCommand(text);
      textInput.value = '';
      textInput.blur();
    }
  });

  textInput.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {
      e.preventDefault();
      sendBtn.click();
    }
  });

  // --- Shortcut buttons ---
  var shortcutBtns = document.querySelectorAll('.shortcut-btn');
  for (var i = 0; i < shortcutBtns.length; i++) {
    (function (btn) {
      btn.addEventListener('click', function () {
        var cmd = btn.getAttribute('data-cmd');
        if (cmd) {
          sendCommand(cmd);
          // Brief visual feedback
          btn.style.transform = 'scale(0.94)';
          setTimeout(function () {
            btn.style.transform = '';
          }, 150);
        }
      });
    })(shortcutBtns[i]);
  }

  // ========== Toast ==========
  function showToast(text) {
    var toast = document.createElement('div');
    toast.className = 'toast glass';
    toast.textContent = text;
    document.body.appendChild(toast);

    // Trigger reflow then animate in
    requestAnimationFrame(function () {
      requestAnimationFrame(function () {
        toast.classList.add('show');
      });
    });

    setTimeout(function () {
      toast.classList.remove('show');
      setTimeout(function () {
        if (toast.parentNode) {
          toast.parentNode.removeChild(toast);
        }
      }, 300);
    }, 2500);
  }

  // ========== Initialize ==========
  initSpeechRecognition();
  connect();
})();
