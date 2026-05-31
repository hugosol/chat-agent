(function () {
    'use strict';

    var els = {
        panel:             document.getElementById('flashcardPanel'),
        toggle:            document.getElementById('flashcardToggle'),
        close:             document.getElementById('flashcardClose'),
        stage1:            document.getElementById('flashcardStage1'),
        stage2:            document.getElementById('flashcardStage2'),
        frontInput:        document.getElementById('flashcardFront'),
        continueBtn:       document.getElementById('flashcardContinue'),
        backInput:         document.getElementById('flashcardBack'),
        tagChips:          document.getElementById('flashcardTagChips'),
        tagInput:          document.getElementById('flashcardTagInput'),
        tagSuggestions:    document.getElementById('flashcardTagSuggestions'),
        saveBtn:           document.getElementById('flashcardSave'),
        toast:             document.getElementById('flashcardToast'),
        debugLog:          document.getElementById('debugLog'),
        debugPanel:        document.getElementById('debugPanel'),
    };

    var chips = [];

    function openPanel() {
        if (window.activePanel === 'debug') {
            els.debugLog.classList.add('hidden');
        }
        window.activePanel = 'flashcard';
        els.toggle.style.visibility = 'hidden';
        els.panel.classList.remove('collapsed');
        showStage1();
    }

    function closePanel() {
        els.panel.classList.add('collapsed');
        els.toggle.style.visibility = '';
        window.activePanel = null;
        resetForm();
    }

    function showStage1() {
        els.stage1.classList.remove('hidden');
        els.stage2.classList.add('hidden');
        els.frontInput.focus();
    }

    function showStage2() {
        var front = els.frontInput.value.trim();
        if (!front) return;
        els.stage1.classList.add('hidden');
        els.stage2.classList.remove('hidden');
        els.backInput.focus();
    }

    function resetForm() {
        els.frontInput.value = '';
        els.backInput.value = '';
        els.tagInput.value = '';
        chips = [];
        renderChips();
        els.tagSuggestions.classList.add('hidden');
        showStage1();
    }

    function renderChips() {
        els.tagChips.innerHTML = '';
        for (var i = 0; i < chips.length; i++) {
            var chip = document.createElement('span');
            chip.className = 'flashcard-chip';
            var label = chips[i].name;
            if (chips[i].type === 'deck') {
                label += ' [D]';
            }
            chip.innerHTML = escapeHtml(label) + '<span class="chip-remove" data-index="' + i + '">&times;</span>';
            els.tagChips.appendChild(chip);
        }
    }

    function addChip(tag) {
        if (!tag) return;
        for (var i = 0; i < chips.length; i++) {
            if (chips[i].id === tag.id) return;
        }
        chips.push(tag);
        renderChips();
        els.tagInput.value = '';
        els.tagSuggestions.classList.add('hidden');
    }

    function removeChip(index) {
        chips.splice(index, 1);
        renderChips();
    }

    function fetchAllTags() {
        fetch('/api/tags', { credentials: 'same-origin' })
            .then(function (resp) {
                if (!resp.ok) throw new Error('Failed to load tags');
                return resp.json();
            })
            .then(function (tags) {
                showSuggestions(tags, els.tagInput.value.trim());
            })
            .catch(function () {
                els.tagSuggestions.classList.add('hidden');
            });
    }

    function showSuggestions(allTags, query) {
        var lowerQuery = query.toLowerCase();
        var alreadyAdded = {};
        for (var i = 0; i < chips.length; i++) {
            alreadyAdded[chips[i].id] = true;
        }
        var matches = allTags.filter(function (t) {
            if (alreadyAdded[t.id]) return false;
            if (lowerQuery.length > 0 && t.name.toLowerCase().indexOf(lowerQuery) === -1) return false;
            return true;
        });

        if (matches.length === 0) {
            els.tagSuggestions.classList.add('hidden');
            return;
        }

        els.tagSuggestions.innerHTML = '';
        for (var i = 0; i < Math.min(matches.length, 8); i++) {
            (function (tag) {
                var item = document.createElement('div');
                item.className = 'tag-suggestion-item';
                var label = tag.name;
                if (tag.type === 'deck') {
                    label += ' \uD83D\uDCC1';
                }
                item.textContent = label;
                item.addEventListener('click', function () {
                    addChip(tag);
                });
                els.tagSuggestions.appendChild(item);
            })(matches[i]);
        }
        els.tagSuggestions.classList.remove('hidden');
    }

    function saveCard() {
        var front = els.frontInput.value.trim();
        var back = els.backInput.value.trim();
        if (!front || !back) return;

        var hasDeck = false;
        for (var i = 0; i < chips.length; i++) {
            if (chips[i].type === 'deck') {
                hasDeck = true;
                break;
            }
        }
        if (!hasDeck) {
            alert('至少需要一个牌组标签');
            return;
        }

        var tagIds = chips.map(function (c) { return c.id; });
        var payload = {
            front: front,
            back: back,
            tagIds: tagIds
        };

        els.saveBtn.disabled = true;

        fetch('/api/cards/add', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
            credentials: 'same-origin'
        })
            .then(function (resp) {
                if (resp.status === 422) {
                    return resp.json().then(function (body) {
                        throw { message: (body && body.message) || '校验不通过', status: 422 };
                    });
                }
                if (!resp.ok) {
                    return resp.json().then(function (body) {
                        throw { message: (body && body.message) || 'Save failed', status: resp.status };
                    });
                }
                return resp.json();
            })
            .then(function () {
                showToast();
                closePanel();
            })
            .catch(function (err) {
                if (err.status === 422) {
                    alert(err.message);
                } else {
                    alert('保存失败: ' + err.message);
                }
            })
            .finally(function () {
                els.saveBtn.disabled = false;
            });
    }

    function showToast() {
        els.toast.classList.remove('hidden');
        els.toast.classList.add('show');
        setTimeout(function () {
            els.toast.classList.add('hidden');
            els.toast.classList.remove('show');
        }, 2100);
    }

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    els.toggle.addEventListener('click', function () {
        if (window.activePanel === 'flashcard') {
            closePanel();
        } else {
            openPanel();
        }
    });

    els.close.addEventListener('click', function () {
        closePanel();
    });

    els.continueBtn.addEventListener('click', function () {
        showStage2();
    });

    els.frontInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            showStage2();
        }
    });

    els.saveBtn.addEventListener('click', function () {
        saveCard();
    });

    els.tagInput.addEventListener('focus', function () {
        fetchAllTags();
    });

    els.tagInput.addEventListener('input', function () {
        fetchAllTags();
    });

    els.tagInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            e.preventDefault();
        } else if (e.key === 'Backspace') {
            if (els.tagInput.value === '' && chips.length > 0) {
                removeChip(chips.length - 1);
            }
        }
    });

    els.tagChips.addEventListener('click', function (e) {
        if (e.target.classList.contains('chip-remove')) {
            var idx = parseInt(e.target.getAttribute('data-index'));
            removeChip(idx);
        }
    });
})();
