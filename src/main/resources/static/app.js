(function () {
    'use strict';

    window.activePanel = null;

    var sessionId = null;

    var els = {
        statusIndicator: document.getElementById('statusIndicator'),
        reportModal:     document.getElementById('reportModal'),
        reportContent:   document.getElementById('reportContent'),
        closeReportBtn:  document.getElementById('closeReportBtn'),
        textInputBar:    document.getElementById('textInputBar'),
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

    function handleMessage(msg) {
        switch (msg.type) {
            case 'TOKEN_WARNING':
                debugLog('TOKEN_WARNING usage=' + msg.usage);
                setStatus('Warning: ' + msg.message, 'warning');
                break;

            case 'STATE_UPDATE':
                debugLog('STATE_UPDATE state=' + msg.state + ' token=' + (msg.tokenUsage || 0));
                setStatus(msg.state, msg.state.toLowerCase());
                break;

            case 'SESSION_REPORT':
                debugLog('SESSION_REPORT');
                els.textInputBar.classList.add('hidden');
                showReport(msg.report);
                break;

            case 'ERROR':
                debugLog('ERROR: ' + msg.message);
                setStatus('Error: ' + msg.message, 'error');
                break;

            case 'WS_CLOSED':
                resetUI();
                break;
        }
    }

    function showReport(report) {
        var html = '<div class="report-section"><strong>Overall Assessment:</strong><p>' + escapeHtml(report.summary) + '</p></div>';

        if (report.fluencyScore >= 0) {
            html += '<div class="report-section"><strong>Fluency Score:</strong> ' + report.fluencyScore + '/10</div>';
        }

        html += '<div class="report-section"><strong>Error Summary:</strong><p>' + escapeHtml(report.errorSummary || '') + '</p></div>' +
            '<div class="report-section"><strong>Key Takeaway:</strong><p>' + escapeHtml(report.keyTakeaway || '') + '</p></div>';
        els.reportContent.innerHTML = html;
        els.reportModal.classList.remove('hidden');
    }

    function resetUI() {
        debugLog('resetUI');
        sessionId = null;
        speechSynthesis.cancel();
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

    ChatAgent.registerHandler(handleMessage);

    els.closeReportBtn.addEventListener('click', function () {
        els.reportModal.classList.add('hidden');
        resetUI();
    });

    els.debugToggle.addEventListener('click', function () {
        els.debugLog.classList.toggle('hidden');
        if (els.debugLog.classList.contains('hidden')) {
            window.activePanel = null;
        } else {
            if (window.activePanel === 'flashcard') {
                document.getElementById('flashcardPanel').classList.add('collapsed');
            }
            window.activePanel = 'debug';
        }
    });

    els.debugClear.addEventListener('click', function () {
        els.debugLog.innerHTML = '';
    });

    document.addEventListener('visibilitychange', function () {
        if (!document.hidden && sessionId) {
            debugLog('visibilitychange: resuming session ' + sessionId);
            ChatAgent.send({ type: 'RESUME_SESSION', sessionId: sessionId });
        }
    });
})();
