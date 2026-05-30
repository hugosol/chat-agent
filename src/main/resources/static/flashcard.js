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
        els.panel.classList.remove('collapsed');
        showStage1();
    }

    function closePanel() {
        els.panel.classList.add('collapsed');
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
            chip.innerHTML = escapeHtml(chips[i]) + '<span class="chip-remove" data-index="' + i + '">&times;</span>';
            els.tagChips.appendChild(chip);
        }
    }

    function addChip(name) {
        name = name.trim();
        if (!name) return;
        for (var i = 0; i < chips.length; i++) {
            if (chips[i] === name) return;
        }
        chips.push(name);
        renderChips();
        els.tagInput.value = '';
        els.tagSuggestions.classList.add('hidden');
    }

    function removeChip(index) {
        chips.splice(index, 1);
        renderChips();
    }

    function fetchTags(query) {
        fetch('/api/tags', { credentials: 'same-origin' })
            .then(function (resp) {
                if (!resp.ok) throw new Error('Failed to load tags');
                return resp.json();
            })
            .then(function (tags) {
                showSuggestions(tags, query);
            })
            .catch(function () {
                els.tagSuggestions.classList.add('hidden');
            });
    }

    function showSuggestions(tags, query) {
        var lowerQuery = query.toLowerCase();
        var matches = tags.filter(function (t) {
            return t.name.toLowerCase().indexOf(lowerQuery) !== -1
                && chips.indexOf(t.name) === -1;
        });

        if (matches.length === 0) {
            els.tagSuggestions.classList.add('hidden');
            return;
        }

        els.tagSuggestions.innerHTML = '';
        for (var i = 0; i < Math.min(matches.length, 5); i++) {
            var item = document.createElement('div');
            item.className = 'tag-suggestion-item';
            item.textContent = matches[i].name;
            item.addEventListener('click', function () {
                addChip(this.textContent);
            });
            els.tagSuggestions.appendChild(item);
        }
        els.tagSuggestions.classList.remove('hidden');
    }

    function saveCard() {
        var front = els.frontInput.value.trim();
        var back = els.backInput.value.trim();
        if (!front || !back) return;

        var payload = {
            front: front,
            back: back,
            tags: chips.slice()
        };

        els.saveBtn.disabled = true;

        fetch('/api/cards/add', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
            credentials: 'same-origin'
        })
            .then(function (resp) {
                if (!resp.ok) throw new Error('Save failed');
                return resp.json();
            })
            .then(function () {
                showToast();
                closePanel();
            })
            .catch(function (err) {
                alert('保存失败: ' + err.message);
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

    els.tagInput.addEventListener('input', function () {
        var query = els.tagInput.value.trim();
        if (query.length > 0) {
            fetchTags(query);
        } else {
            els.tagSuggestions.classList.add('hidden');
        }
    });

    els.tagInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            var query = els.tagInput.value.trim();
            if (query) {
                addChip(query);
            }
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
