(function () {
    'use strict';

    var MAX_VISIBLE_MSGS = 10;
    var WS_URL = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws/coach';

    var ws = null;
    var sessionId = null;
    var synth = window.speechSynthesis;
    var isMuted = false;
    var messageCount = 0;

    var els = {
        tokenBar:        document.getElementById('tokenBar'),
        tokenPct:        document.getElementById('tokenPct'),
        scenarioInfo:    document.getElementById('scenarioInfo'),
        chatArea:        document.getElementById('chatArea'),
        messages:        document.getElementById('messages'),
        earlierMarker:   document.getElementById('earlierMarker'),
        showEarlierBtn:  document.getElementById('showEarlierBtn'),
        statusIndicator: document.getElementById('statusIndicator'),
        scenarioSelect:  document.getElementById('scenarioSelect'),
        personaSelect:   document.getElementById('personaSelect'),
        startBtn:        document.getElementById('startBtn'),
        endBtn:          document.getElementById('endBtn'),
        reportModal:     document.getElementById('reportModal'),
        reportContent:   document.getElementById('reportContent'),
        newSessionBtn:   document.getElementById('newSessionBtn'),
        closeReportBtn:  document.getElementById('closeReportBtn'),
        textInputBar:    document.getElementById('textInputBar'),
        textInput:       document.getElementById('textInput'),
        sendTextBtn:     document.getElementById('sendTextBtn'),
        debugPanel:      document.getElementById('debugPanel'),
        debugLog:        document.getElementById('debugLog'),
        debugToggle:     document.getElementById('debugToggle'),
        debugClear:      document.getElementById('debugClear'),
    };

    function debugLog(msg) {
        var now = new Date();
        var h = String(now.getHours()).padStart(2, '0');
        var m = String(now.getMinutes()).padStart(2, '0');
        var s = String(now.getSeconds()).padStart(2, '0');
        var ms = String(now.getMilliseconds()).padStart(3, '0');
        var line = '[' + h + ':' + m + ':' + s + '.' + ms + '] ' + msg;
        var div = document.createElement('div');
        div.textContent = line;
        els.debugLog.appendChild(div);
        els.debugLog.scrollTop = els.debugLog.scrollHeight;
    }

    function connect() {
        debugLog('connect()');
        ws = new WebSocket(WS_URL);
        ws.onopen = function () {
            debugLog('ws.onopen');
            setStatus('Connected', 'connected');
        };
        ws.onclose = function () {
            debugLog('ws.onclose');
            setStatus('Disconnected', 'disconnected');
            resetUI();
        };
        ws.onerror = function () {
            debugLog('ws.onerror');
            setStatus('Connection error', 'disconnected');
        };
        ws.onmessage = function (event) {
            debugLog('ws.onmessage: ' + event.data.slice(0, 120));
            handleMessage(JSON.parse(event.data));
        };
    }

    function handleMessage(msg) {
        switch (msg.type) {
            case 'SESSION_STARTED':
                debugLog('SESSION_STARTED id=' + msg.sessionId + ' scenario=' + msg.scenario);
                sessionId = msg.sessionId;
                els.scenarioInfo.textContent = msg.scenario + ' | ' + msg.persona;
                els.startBtn.disabled = true;
                els.endBtn.disabled = false;
                els.scenarioSelect.disabled = true;
                els.personaSelect.disabled = true;
                messageCount = 0;
                els.messages.innerHTML = '';
                els.earlierMarker.classList.add('hidden');
                els.reportModal.classList.add('hidden');
                showTextInput();
                break;

            case 'STATE_UPDATE':
                debugLog('STATE_UPDATE state=' + msg.state + ' token=' + (msg.tokenUsage || 0));
                setStatus(msg.state, msg.state.toLowerCase());
                updateTokenBar(msg.tokenUsage);
                break;

            case 'AGENT_RESPONSE':
                debugLog('AGENT_RESPONSE text=' + (msg.conversationText || '').slice(0, 60) + ' corrections=' + ((msg.corrections && msg.corrections.length) || 0));
                addMessage('Agent', msg.conversationText, msg.conversationText);
                if (msg.corrections && msg.corrections.length > 0) {
                    var corrText = msg.corrections.map(function (c) {
                        return c.type + ': "' + c.original + '" \u2192 "' + c.corrected + '"';
                    }).join('\n');
                    addMessage('Correction', corrText);
                }
                updateTokenBar(msg.tokenUsage);
                try { speakText(msg.conversationText); } catch (e) { debugLog('TTS error: ' + e); }
                showTextInput();
                break;

            case 'TOKEN_WARNING':
                debugLog('TOKEN_WARNING usage=' + msg.usage);
                setStatus('Warning: ' + msg.message, 'warning');
                break;

            case 'SESSION_REPORT':
                debugLog('SESSION_REPORT');
                els.textInputBar.classList.add('hidden');
                showReport(msg.report);
                els.startBtn.disabled = false;
                els.endBtn.disabled = true;
                break;

            case 'ERROR':
                debugLog('ERROR: ' + msg.message);
                setStatus('Error: ' + msg.message, 'error');
                break;
        }
    }

    function showTextInput() {
        debugLog('showTextInput');
        if (!sessionId) return;
        els.textInputBar.classList.remove('hidden');
        els.textInput.value = '';
        els.textInput.disabled = false;
        els.sendTextBtn.disabled = false;
        setStatus('Type your message', 'connected');
        setTimeout(function () {
            els.textInput.focus();
        }, 100);
    }

    function sendTextInput() {
        var text = els.textInput.value.trim();
        if (!text || !sessionId) return;
        debugLog('sendTextInput: "' + text.slice(0, 60) + '"');
        addMessage('You', text);
        ws.send(JSON.stringify({ type: 'USER_INPUT', text: text }));
        els.textInput.value = '';
        els.textInput.disabled = true;
        els.sendTextBtn.disabled = true;
        els.textInput.placeholder = 'Waiting for reply...';
        setStatus('Processing...', 'processing');
    }

    function speakText(text) {
        if (isMuted) return;
        synth.cancel();
        var utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'en-US';
        utterance.rate = 0.95;
        synth.speak(utterance);
    }

    function addMessage(role, content, ttsText) {
        messageCount++;
        var div = document.createElement('div');
        div.className = 'message ' + role.toLowerCase();
        var html = '<span class="role">' + role + ':</span> ' + escapeHtml(content);
        if (role === 'Agent' && ttsText) {
            html += ' <button class="btn-play" title="Read aloud">🔊</button>';
        }
        div.innerHTML = html;
        if (role === 'Agent' && ttsText) {
            var btn = div.querySelector('.btn-play');
            btn.addEventListener('click', function (e) {
                e.stopPropagation();
                debugLog('BTN play TTS');
                speakText(ttsText);
            });
        }
        els.messages.appendChild(div);

        if (messageCount > MAX_VISIBLE_MSGS) {
            els.earlierMarker.classList.remove('hidden');
            var children = els.messages.children;
            for (var i = 0; i < children.length - MAX_VISIBLE_MSGS; i++) {
                children[i].classList.add('collapsed');
            }
        }

        els.chatArea.scrollTop = els.chatArea.scrollHeight;
    }

    function updateTokenBar(usage) {
        var pct = Math.min(100, Math.round(usage * 100));
        els.tokenBar.style.width = pct + '%';
        els.tokenPct.textContent = pct + '%';
        if (pct >= 80) {
            els.tokenBar.style.backgroundColor = '#e74c3c';
        } else if (pct >= 50) {
            els.tokenBar.style.backgroundColor = '#f39c12';
        } else {
            els.tokenBar.style.backgroundColor = '#27ae60';
        }
    }

    function showReport(report) {
        els.reportContent.innerHTML =
            '<div class="report-section"><strong>Overall Assessment:</strong><p>' + escapeHtml(report.summary) + '</p></div>' +
            '<div class="report-section"><strong>Fluency Score:</strong> ' + report.fluencyScore + '/10</div>' +
            '<div class="report-section"><strong>Error Summary:</strong><p>' + escapeHtml(report.errorSummary || '') + '</p></div>' +
            '<div class="report-section"><strong>Vocabulary Suggestions:</strong><p>' + escapeHtml(report.vocabularySuggestions || '') + '</p></div>' +
            '<div class="report-section"><strong>Key Takeaway:</strong><p>' + escapeHtml(report.keyTakeaway || '') + '</p></div>';
        els.reportModal.classList.remove('hidden');
    }

    function resetUI() {
        debugLog('resetUI');
        sessionId = null;
        els.startBtn.disabled = false;
        els.endBtn.disabled = true;
        els.scenarioSelect.disabled = false;
        els.personaSelect.disabled = false;
        els.scenarioInfo.textContent = '';
        els.tokenBar.style.width = '0%';
        els.tokenPct.textContent = '0%';
        els.textInputBar.classList.add('hidden');
        els.textInput.value = '';
        synth.cancel();
    }

    function setStatus(text, cls) {
        els.statusIndicator.textContent = text;
        els.statusIndicator.className = cls;
    }

    function escapeHtml(text) {
        if (!text) return '';
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    els.sendTextBtn.addEventListener('click', function () {
        sendTextInput();
    });

    els.textInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            sendTextInput();
        }
    });

    els.startBtn.addEventListener('click', function () {
        debugLog('BTN start session');
        if (!ws || ws.readyState !== WebSocket.OPEN) {
            connect();
            setTimeout(function () {
                sendStart();
            }, 300);
        } else {
            sendStart();
        }
    });

    function sendStart() {
        debugLog('sendStart: ' + els.scenarioSelect.value + ' ' + els.personaSelect.value);
        ws.send(JSON.stringify({
            type: 'START_SESSION',
            scenario: els.scenarioSelect.value,
            persona: els.personaSelect.value
        }));
    }

    els.endBtn.addEventListener('click', function () {
        debugLog('BTN end session');
        if (ws && ws.readyState === WebSocket.OPEN) {
            els.textInputBar.classList.add('hidden');
            synth.cancel();
            ws.send(JSON.stringify({ type: 'END_SESSION' }));
        }
    });

    els.showEarlierBtn.addEventListener('click', function () {
        var collapsed = document.querySelectorAll('.message.collapsed');
        for (var i = 0; i < collapsed.length; i++) {
            collapsed[i].classList.remove('collapsed');
        }
        els.earlierMarker.classList.add('hidden');
    });

    els.newSessionBtn.addEventListener('click', function () {
        els.reportModal.classList.add('hidden');
        els.messages.innerHTML = '';
        els.earlierMarker.classList.add('hidden');
        messageCount = 0;
        sendStart();
    });

    els.closeReportBtn.addEventListener('click', function () {
        els.reportModal.classList.add('hidden');
        resetUI();
    });

    els.debugToggle.addEventListener('click', function () {
        els.debugLog.classList.toggle('hidden');
    });

    els.debugClear.addEventListener('click', function () {
        els.debugLog.innerHTML = '';
    });

    connect();
})();
