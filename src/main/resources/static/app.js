(function () {
    'use strict';

    var MAX_VISIBLE_MSGS = 10;
    var WS_URL = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws/coach';

    var ws = null;
    var sessionId = null;
    var synth = window.speechSynthesis;
    var messageCount = 0;
    var turnCounter = 0;
    var streamBubbles = {};
    var correctionCount = 0;

    var els = {
        tokenBar:        document.getElementById('tokenBar'),
        tokenPct:        document.getElementById('tokenPct'),
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
        correctionSidebar:      document.getElementById('correctionSidebar'),
        correctionSidebarContent: document.getElementById('correctionSidebarContent'),
        correctionSidebarToggle:  document.getElementById('correctionSidebarToggle'),
        correctionShowBtn:   document.getElementById('correctionShowBtn'),
        correctionBadge:     document.getElementById('correctionBadge'),
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
            var savedSessionId = localStorage.getItem('sessionId');
            if (savedSessionId) {
                debugLog('resuming session: ' + savedSessionId);
                ws.send(JSON.stringify({ type: 'RESUME_SESSION', sessionId: savedSessionId }));
            }
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
                localStorage.setItem('sessionId', msg.sessionId);
                els.startBtn.disabled = true;
                els.endBtn.disabled = false;
                els.scenarioSelect.disabled = true;
                els.personaSelect.disabled = true;
                messageCount = 0;
                turnCounter = 0;
                streamBubbles = {};
                els.messages.innerHTML = '';
                els.correctionSidebarContent.innerHTML = '<div class="correction-sidebar-empty">No corrections yet.</div>';
                correctionCount = 0;
                updateCorrectionBadge();
                els.earlierMarker.classList.add('hidden');
                els.reportModal.classList.add('hidden');
                showTextInput();
                break;

            case 'AGENT_STREAM_DELTA':
                handleStreamDelta(msg.messageId, msg.delta);
                break;

            case 'AGENT_STREAM_END':
                handleStreamEnd(msg.messageId, msg.text, msg.tokenUsage);
                break;

            case 'CORRECTION_RESULT':
                handleCorrectionResult(msg.messageId, msg.corrections);
                break;

            case 'TOKEN_WARNING':
                debugLog('TOKEN_WARNING usage=' + msg.usage);
                setStatus('Warning: ' + msg.message, 'warning');
                break;

            case 'STATE_UPDATE':
                debugLog('STATE_UPDATE state=' + msg.state + ' token=' + (msg.tokenUsage || 0));
                setStatus(msg.state, msg.state.toLowerCase());
                updateTokenBar(msg.tokenUsage);
                break;

            case 'SESSION_RESUMED':
                handleSessionResumed(msg);
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

    function handleStreamDelta(msgId, delta) {
        var bubble = streamBubbles[msgId];
        if (!bubble) {
            bubble = createMessageElement('Agent', '', msgId, true);
            els.messages.appendChild(bubble);
            streamBubbles[msgId] = bubble;
            messageCount++;
        }
        var contentEl = bubble.querySelector('.content-text');
        contentEl.textContent += delta;
        els.chatArea.scrollTop = els.chatArea.scrollHeight;
    }

    function handleStreamEnd(msgId, fullText, tokenUsage) {
        var bubble = streamBubbles[msgId];
        if (bubble) {
            var contentEl = bubble.querySelector('.content-text');
            contentEl.textContent = fullText;
            addPlayButton(bubble, fullText);
            delete streamBubbles[msgId];
            handleCollapse();
        }
        updateTokenBar(tokenUsage);
        try { speakText(fullText); } catch (e) { debugLog('TTS error: ' + e); }
        showTextInput();
    }

    function handleCorrectionResult(msgId, corrections) {
        if (!corrections || corrections.length === 0) return;

        var userBubble = els.messages.querySelector('[data-message-id="' + msgId + '"].user');
        if (!userBubble) {
            debugLog('correction: no user bubble for msgId=' + msgId);
            return;
        }

        var summary = corrections.map(function (c, i) {
            return (i + 1) + '. ' + c.original + ' \u2192 ' + c.corrected;
        }).join('\n');

        var corrBubble = createCorrectionBubble(summary, msgId);
        userBubble.insertAdjacentElement('afterend', corrBubble);
        messageCount++;

        for (var i = 0; i < corrections.length; i++) {
            addCorrectionSidebarItem(corrections[i]);
        }

        handleCollapse();
        els.chatArea.scrollTop = els.chatArea.scrollHeight;
    }

    function handleSessionResumed(msg) {
        debugLog('SESSION_RESUMED sessionId=' + msg.sessionId);
        sessionId = msg.sessionId;
        localStorage.setItem('sessionId', msg.sessionId);
        els.startBtn.disabled = true;
        els.endBtn.disabled = false;
        els.scenarioSelect.disabled = true;
        els.personaSelect.disabled = true;
        messageCount = 0;
        streamBubbles = {};
        els.messages.innerHTML = '';
        els.correctionSidebarContent.innerHTML = '';
        correctionCount = 0;
        els.earlierMarker.classList.add('hidden');
        els.reportModal.classList.add('hidden');

        turnCounter = 0;
        if (msg.messages && msg.messages.length > 0) {
            for (var i = 0; i < msg.messages.length; i++) {
                var m = msg.messages[i];
                if (m.role === 'USER') {
                    turnCounter++;
                    var userDiv = createMessageElement(m.role, m.content, turnCounter, false);
                    els.messages.appendChild(userDiv);
                    messageCount++;
                } else {
                    rebuildMessage(m.role, m.content);
                }
            }
        }

        if (msg.corrections && msg.corrections.length > 0) {
            var byMsgId = {};
            for (var j = 0; j < msg.corrections.length; j++) {
                var c = msg.corrections[j];
                addCorrectionSidebarItem(c);
                var mid = c.messageId || 0;
                if (!byMsgId[mid]) byMsgId[mid] = [];
                byMsgId[mid].push(c);
            }
            for (var mid in byMsgId) {
                if (mid === '0') continue;
                var corrs = byMsgId[mid];
                var userBubble = els.messages.querySelector('[data-message-id="' + mid + '"].user');
                if (userBubble) {
                    var summary = corrs.map(function (c, i) {
                        return (i + 1) + '. ' + c.original + ' \u2192 ' + c.corrected;
                    }).join('\n');
                    var corrBubble = createCorrectionBubble(summary, parseInt(mid));
                    userBubble.insertAdjacentElement('afterend', corrBubble);
                    messageCount++;
                }
            }
        }
        updateCorrectionBadge();

        updateTokenBar(msg.tokenUsage);
        showTextInput();
    }

    function rebuildMessage(role, content) {
        var div = createMessageElement(role, content, null, false);
        if (role.toLowerCase() === 'agent') {
            addPlayButton(div, content);
        }
        els.messages.appendChild(div);
        messageCount++;
    }

    function createMessageElement(role, content, msgId, isStreaming) {
        var div = document.createElement('div');
        var roleLower = role.toLowerCase();
        div.className = 'message ' + roleLower;
        if (msgId != null && roleLower === 'user') div.setAttribute('data-message-id', msgId);
        if (msgId != null && roleLower === 'agent') div.setAttribute('data-message-id', msgId);
        var html = '<span class="role">' + role + ':</span> ';
        html += '<span class="content-text">' + escapeHtml(content) + '</span>';
        if (isStreaming) {
            html += '<span class="stream-cursor">|</span>';
        }
        div.innerHTML = html;
        return div;
    }

    function createCorrectionBubble(summary, msgId) {
        var div = document.createElement('div');
        div.className = 'message correction-bubble';
        div.setAttribute('data-message-id', msgId);
        div.innerHTML = '<span class="role">Correction:</span> <span class="content-text">' + escapeHtml(summary) + '</span>';
        return div;
    }

    function addPlayButton(bubble, ttsText) {
        var cursor = bubble.querySelector('.stream-cursor');
        if (cursor) cursor.remove();
        var btn = document.createElement('button');
        btn.className = 'btn-play';
        btn.title = 'Read aloud';
        btn.textContent = '🔊';
        btn.addEventListener('click', function (e) {
            e.stopPropagation();
            debugLog('BTN play TTS');
            speakText(ttsText);
        });
        bubble.appendChild(btn);
    }

    function addCorrectionSidebarItem(c) {
        var empty = els.correctionSidebarContent.querySelector('.correction-sidebar-empty');
        if (empty) empty.remove();

        var item = document.createElement('div');
        item.className = 'correction-item';
        item.innerHTML =
            '<div class="correction-type">' + escapeHtml(c.type) + '</div>' +
            '<div class="correction-detail">' +
                '<span class="correction-original">' + escapeHtml(c.original) + '</span>' +
                '<span class="correction-arrow">\u2192</span>' +
                '<span class="correction-corrected">' + escapeHtml(c.corrected) + '</span>' +
            '</div>' +
            '<div class="correction-explanation">' + escapeHtml(c.explanation || '') + '</div>';
        els.correctionSidebarContent.appendChild(item);
        correctionCount++;
        updateCorrectionBadge();
    }

    function updateCorrectionBadge() {
        els.correctionBadge.textContent = correctionCount;
        if (correctionCount > 0) {
            els.correctionBadge.style.color = '#e94560';
        } else {
            els.correctionBadge.style.color = '';
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
        turnCounter++;
        var msgId = turnCounter;
        debugLog('sendTextInput msgId=' + msgId + ' "' + text.slice(0, 60) + '"');

        var userBubble = createMessageElement('User', text, msgId, false);
        els.messages.appendChild(userBubble);
        messageCount++;

        ws.send(JSON.stringify({ type: 'USER_INPUT', text: text, messageId: msgId }));
        els.textInput.value = '';
        els.textInput.disabled = true;
        els.sendTextBtn.disabled = true;
        els.textInput.placeholder = 'Waiting for reply...';
        setStatus('Processing...', 'processing');
        els.chatArea.scrollTop = els.chatArea.scrollHeight;
    }

    function speakText(text) {
        synth.cancel();
        var utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'en-US';
        utterance.rate = 0.95;
        synth.speak(utterance);
    }

    function handleCollapse() {
        if (messageCount > MAX_VISIBLE_MSGS) {
            els.earlierMarker.classList.remove('hidden');
            var children = els.messages.children;
            for (var i = 0; i < children.length - MAX_VISIBLE_MSGS; i++) {
                children[i].classList.add('collapsed');
            }
        }
    }

    function updateTokenBar(usage) {
        if (usage == null) return;
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
        els.tokenBar.style.width = '0%';
        els.tokenPct.textContent = '0%';
        els.textInputBar.classList.add('hidden');
        els.textInput.value = '';
        els.textInput.placeholder = 'Type or use 🎤 on keyboard...';
        streamBubbles = {};
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
        els.correctionSidebarContent.innerHTML = '<div class="correction-sidebar-empty">No corrections yet.</div>';
        correctionCount = 0;
        updateCorrectionBadge();
        els.earlierMarker.classList.add('hidden');
        messageCount = 0;
        streamBubbles = {};
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

    els.correctionSidebarToggle.addEventListener('click', function () {
        els.correctionSidebar.classList.toggle('collapsed');
        var collapsed = els.correctionSidebar.classList.contains('collapsed');
        els.correctionShowBtn.classList.toggle('hidden', !collapsed);
    });

    els.correctionShowBtn.addEventListener('click', function () {
        els.correctionSidebar.classList.toggle('collapsed');
        var collapsed = els.correctionSidebar.classList.contains('collapsed');
        els.correctionShowBtn.classList.toggle('hidden', !collapsed);
    });

    connect();
})();
