(function() {
  'use strict';

  // WebSocket connects to port 9871 (WS server) on same hostname
  var wsUrl = 'ws://' + location.hostname + ':9871';

  // DOM
  var statusDot = document.getElementById('statusDot');
  var statusText = document.getElementById('statusText');
  var previewImage = document.getElementById('previewImage');
  var previewPlaceholder = document.getElementById('previewPlaceholder');
  var currentUrl = document.getElementById('currentUrl');
  var aiStatus = document.getElementById('aiStatus');
  var aiIcon = document.getElementById('aiIcon');
  var aiText = document.getElementById('aiText');
  var interruptBtn = document.getElementById('interruptBtn');
  var aiLog = document.getElementById('aiLog');
  var aiLogList = document.getElementById('aiLogList');
  var aiLogStep = document.getElementById('aiLogStep');
  var voiceBtn = document.getElementById('voiceBtn');
  var voiceRing = document.getElementById('voiceRing');
  var voiceHint = document.getElementById('voiceHint');
  var textInput = document.getElementById('textInput');
  var sendBtn = document.getElementById('sendBtn');

  // State
  var ws = null;
  var isRecording = false;
  var recognition = null;
  var reconnectTimer = null;
  var reconnectDelay = 3000;
  var stepCount = 0;

  // ====== WebSocket ======
  function connect() {
    if (ws && (ws.readyState === WebSocket.CONNECTING || ws.readyState === WebSocket.OPEN)) return;
    try { ws = new WebSocket(wsUrl); } catch(e) {
      statusDot.classList.remove('connected'); statusText.textContent = '连接失败';
      scheduleReconnect(); return;
    }
    ws.onopen = function() {
      statusDot.classList.add('connected'); statusText.textContent = '已连接TV';
      reconnectDelay = 3000;
    };
    ws.onmessage = function(event) {
      try { handleMessage(JSON.parse(event.data)); } catch(e) {}
    };
    ws.onclose = function() {
      statusDot.classList.remove('connected'); statusText.textContent = '已断开 · 重连中...';
      scheduleReconnect();
    };
    ws.onerror = function() { statusDot.classList.remove('connected'); statusText.textContent = '连接失败'; };
  }

  function scheduleReconnect() {
    if (reconnectTimer) return;
    reconnectTimer = setTimeout(function() { reconnectTimer = null; connect(); }, reconnectDelay);
    reconnectDelay = Math.min(reconnectDelay * 1.5, 30000);
  }

  function safeSend(obj) {
    if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj));
  }

  // ====== Message Handler ======
  function handleMessage(msg) {
    switch(msg.type) {
      case 'frame':
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
    }
  }

  // ====== AI Status ======
  function updateAiStatus(text, level) {
    aiText.textContent = text;
    aiStatus.className = 'ai-status glass ' + level;
    if (level === 'thinking') {
      aiIcon.classList.add('spinning');
      interruptBtn.style.display = 'block';
      aiLog.style.display = 'block';
      stepCount++;
      aiLogStep.textContent = '第' + stepCount + '步';
      addLogEntry(text, 'thinking');
    } else {
      aiIcon.classList.remove('spinning');
      if (level === 'done' || level === 'error') {
        interruptBtn.style.display = 'none';
        addLogEntry(text, level);
        stepCount = 0;
      }
    }
  }

  function addLogEntry(text, type) {
    var entry = document.createElement('div');
    entry.className = 'ai-log-entry';
    if (type === 'tool') entry.classList.add('tool');
    if (type === 'done') entry.classList.add('done');
    if (type === 'error') entry.classList.add('error');
    var time = new Date();
    var ts = time.getHours().toString().padStart(2,'0') + ':' +
             time.getMinutes().toString().padStart(2,'0') + ':' +
             time.getSeconds().toString().padStart(2,'0');
    entry.textContent = ts + ' ' + text;
    aiLogList.appendChild(entry);
    aiLogList.scrollTop = aiLogList.scrollHeight;
    while (aiLogList.children.length > 30) aiLogList.removeChild(aiLogList.firstChild);
  }

  // ====== Commands ======
  function sendCommand(text) {
    if (!text || !text.trim()) return;
    text = text.trim();
    safeSend({ type: 'voice', text: text });
    stepCount = 0;
    aiLogList.innerHTML = '';
    aiLog.style.display = 'block';
    updateAiStatus('发送: "' + text + '"', 'thinking');
  }

  // ====== Interrupt ======
  interruptBtn.addEventListener('click', function() {
    safeSend({ type: 'interrupt' });
    updateAiStatus('正在打断...', 'info');
  });

  // ====== Speech Recognition ======
  function initSpeechRecognition() {
    var SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
      voiceHint.textContent = '浏览器不支持语音';
      voiceBtn.style.opacity = '0.4'; voiceBtn.style.pointerEvents = 'none';
      return;
    }
    recognition = new SR();
    recognition.lang = 'zh-CN'; recognition.continuous = false;
    recognition.interimResults = true; recognition.maxAlternatives = 1;
    recognition.onresult = function(event) {
      var transcript = event.results[0][0].transcript;
      if (event.results[0].isFinal) {
        sendCommand(transcript);
        voiceHint.textContent = '已发送: ' + transcript;
        setTimeout(function() { if (!isRecording) voiceHint.textContent = '长按说话'; }, 3000);
      } else {
        voiceHint.textContent = '识别中: ' + transcript;
      }
    };
    recognition.onerror = function(event) {
      stopRecording();
      if (event.error === 'no-speech') voiceHint.textContent = '未检测到语音';
      else if (event.error === 'not-allowed') voiceHint.textContent = '请允许麦克风权限';
      else voiceHint.textContent = '识别失败，请重试';
    };
    recognition.onend = function() { stopRecording(); };
  }

  function startRecording() {
    if (!recognition || isRecording) return;
    isRecording = true;
    voiceBtn.classList.add('recording'); voiceRing.classList.add('active');
    voiceHint.textContent = '正在听...';
    try { recognition.start(); } catch(e) { stopRecording(); return; }
    if (navigator.vibrate) navigator.vibrate(50);
  }

  function stopRecording() {
    isRecording = false;
    voiceBtn.classList.remove('recording'); voiceRing.classList.remove('active');
    if (recognition) try { recognition.stop(); } catch(e) {}
  }

  // ====== Events ======
  var pressTimer = null;
  voiceBtn.addEventListener('touchstart', function(e) {
    e.preventDefault();
    pressTimer = setTimeout(function() { startRecording(); }, 200);
  }, {passive:false});
  voiceBtn.addEventListener('touchend', function(e) {
    e.preventDefault(); clearTimeout(pressTimer); pressTimer = null;
    if (isRecording) stopRecording();
  }, {passive:false});
  voiceBtn.addEventListener('touchcancel', function() {
    clearTimeout(pressTimer); pressTimer = null;
    if (isRecording) stopRecording();
  });
  voiceBtn.addEventListener('mousedown', function() {
    if ('ontouchstart' in window) return;
    pressTimer = setTimeout(function() { startRecording(); }, 200);
  });
  voiceBtn.addEventListener('mouseup', function() {
    if ('ontouchstart' in window) return;
    clearTimeout(pressTimer); pressTimer = null;
    if (isRecording) stopRecording();
  });

  sendBtn.addEventListener('click', function() {
    var text = textInput.value.trim();
    if (text) { sendCommand(text); textInput.value = ''; textInput.blur(); }
  });
  textInput.addEventListener('keydown', function(e) {
    if (e.key === 'Enter') { e.preventDefault(); sendBtn.click(); }
  });

  var shortcutBtns = document.querySelectorAll('.shortcut-btn');
  for (var i = 0; i < shortcutBtns.length; i++) {
    (function(btn) {
      btn.addEventListener('click', function() {
        var cmd = btn.getAttribute('data-cmd');
        if (cmd) { sendCommand(cmd); btn.style.transform='scale(0.94)'; setTimeout(function(){btn.style.transform='';},150); }
      });
    })(shortcutBtns[i]);
  }

  // ====== Toast ======
  function showToast(text) {
    var toast = document.createElement('div');
    toast.className = 'toast glass'; toast.textContent = text;
    document.body.appendChild(toast);
    requestAnimationFrame(function(){requestAnimationFrame(function(){toast.classList.add('show');});});
    setTimeout(function(){
      toast.classList.remove('show');
      setTimeout(function(){ if(toast.parentNode) toast.parentNode.removeChild(toast); }, 300);
    }, 2500);
  }

  // ====== Init ======
  initSpeechRecognition();
  connect();
})();
