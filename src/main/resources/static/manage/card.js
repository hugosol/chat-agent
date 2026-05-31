(function () {
    'use strict';

    var container = document.getElementById('cardsTab');
    var currentSearch = '';
    var currentDeckId = null;
    var currentSort = 'front,asc';
    var currentPage = 0;
    var totalPages = 0;

    function init() {
        currentSearch = '';
        currentDeckId = null;
        currentSort = 'front,asc';
        currentPage = 0;
        loadDecks();
        loadCards();
    }

    function destroy() {
        container.innerHTML = '<div class="empty-state">暂无卡片，创建第一张卡片</div>';
    }

    function loadDecks() {
        fetch('/api/tags?type=deck', { credentials: 'same-origin' })
            .then(function (r) { return r.json(); })
            .then(function (tags) {
                renderDeckChips(tags);
            })
            .catch(function () {});
    }

    function loadCards() {
        var params = '?page=' + currentPage + '&size=20&sort=' + encodeURIComponent(currentSort);
        if (currentSearch) params += '&search=' + encodeURIComponent(currentSearch);
        if (currentDeckId) params += '&deckId=' + encodeURIComponent(currentDeckId);

        fetch('/api/cards' + params, { credentials: 'same-origin' })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                totalPages = data.totalPages || 0;
                renderCards(data);
            })
            .catch(function () {
                container.innerHTML = '<div class="empty-state">加载失败</div>';
            });
    }

    function renderToolbar() {
        var sortNameActive = currentSort.indexOf('front') === 0 ? ' active' : '';
        var sortTimeActive = currentSort.indexOf('createTime') === 0 ? ' active' : '';

        return '<div class="manage-toolbar">' +
            '<div class="search-row">' +
                '<input type="text" id="cardSearch" class="card-search" placeholder="搜索卡片..." value="' + escapeHtml(currentSearch) + '">' +
                '<div class="sort-btns">' +
                    '<button class="sort-btn' + sortNameActive + '" data-sort="front,asc">A&rarr;Z</button>' +
                    '<button class="sort-btn' + sortTimeActive + '" data-sort="createTime,desc">Time</button>' +
                '</div>' +
            '</div>' +
            '<div class="deck-chips" id="deckChips"></div>' +
            '</div>';
    }

    function renderDeckChips(tags) {
        var chipsEl = document.getElementById('deckChips');
        if (!chipsEl) return;

        if (tags.length === 0) {
            chipsEl.innerHTML = '<span style="color:#555;font-size:0.75em;">暂无牌组，创建牌组</span>';
            return;
        }

        var html = '';
        for (var i = 0; i < tags.length; i++) {
            var t = tags[i];
            var activeClass = (currentDeckId === t.id) ? ' active' : '';
            html += '<span class="deck-chip' + activeClass + '" data-deck-id="' + t.id + '">' + escapeHtml(t.name) + '</span>';
        }
        chipsEl.innerHTML = html;

        chipsEl.querySelectorAll('.deck-chip').forEach(function (chip) {
            chip.addEventListener('click', function () {
                var deckId = this.getAttribute('data-deck-id');
                if (currentDeckId === deckId) {
                    currentDeckId = null;
                } else {
                    currentDeckId = deckId;
                }
                currentPage = 0;
                loadDecks();
                loadCards();
            });
        });
    }

    function renderCards(data) {
        var content = data.content || [];
        var html = renderToolbar();

        if (content.length === 0) {
            html += '<div class="empty-state">暂无卡片' +
                '<br><button class="btn btn-primary" onclick="window.manageCards.showCreate()">创建第一张卡片</button></div>';
        } else {
            for (var i = 0; i < content.length; i++) {
                var card = content[i];
                html += '<div class="card-block" data-card=\'' + JSON.stringify(card).replace(/'/g, "\\'") + '\'>' +
                    '<div class="card-front">' + escapeHtml(card.front) + '</div>' +
                    '<div class="card-back">' + escapeHtml(truncate(card.back, 50)) + '</div>' +
                    '<div class="card-tags">' + renderCardTags(card.tags || []) + '</div>' +
                    '<div class="card-actions">' +
                        '<button class="btn-edit-card">Edit</button>' +
                        '<button class="btn-delete-card">Delete</button>' +
                    '</div>' +
                    '</div>';
            }
            html += renderPagination();
        }

        container.innerHTML = html;
        bindCardEvents();
    }

    function renderCardTags(tags) {
        var html = '';
        for (var i = 0; i < tags.length; i++) {
            var t = tags[i];
            var label = t.name;
            if (t.type === 'deck') label += ' [D]';
            html += '<span class="chip">' + escapeHtml(label) + '</span>';
        }
        return html;
    }

    function renderPagination() {
        if (totalPages <= 1) return '';

        var html = '<div class="pagination">';
        html += '<button class="page-prev"' + (currentPage === 0 ? ' disabled' : '') + '>&lt; 上一页</button>';

        for (var p = 0; p < totalPages; p++) {
            if (totalPages > 7 && p > 2 && p < totalPages - 3) {
                if (p === 3) html += '<button disabled>...</button>';
                continue;
            }
            html += '<button class="page-num' + (p === currentPage ? ' active' : '') + '" data-page="' + p + '">' + (p + 1) + '</button>';
        }

        html += '<button class="page-next"' + (currentPage >= totalPages - 1 ? ' disabled' : '') + '>下一页 &gt;</button>';
        html += '</div>';
        return html;
    }

    function bindCardEvents() {
        container.querySelectorAll('.card-block').forEach(function (block) {
            block.addEventListener('click', function (e) {
                if (e.target.closest('.card-actions')) return;
                var cardJson = this.getAttribute('data-card');
                var card = JSON.parse(cardJson);
                showCardDetail(card);
            });
        });

        container.querySelectorAll('.btn-edit-card').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.stopPropagation();
                var block = this.closest('.card-block');
                var card = JSON.parse(block.getAttribute('data-card'));
                showEditModal(card);
            });
        });

        container.querySelectorAll('.btn-delete-card').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.stopPropagation();
                var block = this.closest('.card-block');
                var card = JSON.parse(block.getAttribute('data-card'));
                deleteCard(card);
            });
        });

        var searchInput = container.querySelector('#cardSearch');
        if (searchInput) {
            var searchTimer;
            searchInput.addEventListener('input', function () {
                clearTimeout(searchTimer);
                var self = this;
                searchTimer = setTimeout(function () {
                    currentSearch = self.value.trim();
                    currentPage = 0;
                    loadCards();
                }, 300);
            });
        }

        container.querySelectorAll('.sort-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var newSort = this.getAttribute('data-sort');
                if (newSort === currentSort) {
                    currentSort = currentSort.endsWith(',asc') ? currentSort.replace(',asc', ',desc') : currentSort.replace(',desc', ',asc');
                } else {
                    currentSort = newSort;
                }
                currentPage = 0;
                loadCards();
            });
        });

        container.querySelectorAll('.page-num').forEach(function (btn) {
            btn.addEventListener('click', function () {
                currentPage = parseInt(this.getAttribute('data-page'));
                loadCards();
            });
        });

        var prevBtn = container.querySelector('.page-prev');
        if (prevBtn) prevBtn.addEventListener('click', function () {
            if (currentPage > 0) { currentPage--; loadCards(); }
        });

        var nextBtn = container.querySelector('.page-next');
        if (nextBtn) nextBtn.addEventListener('click', function () {
            if (currentPage < totalPages - 1) { currentPage++; loadCards(); }
        });
    }

    function showCardDetail(card) {
        var cardStateLabels = ['New', 'Learning', 'Review', 'Relearning'];
        var state = cardStateLabels[card.cardState] || card.cardState;
        var tagsHtml = (card.tags || []).map(function (t) {
            return '<span class="chip">' + escapeHtml(t.name) + (t.type === 'deck' ? ' [D]' : '') + '</span>';
        }).join(' ');

        var bodyHtml =
            '<div class="detail-item"><div class="detail-label">Front</div><div class="detail-value">' + escapeHtml(card.front) + '</div></div>' +
            '<div class="detail-item"><div class="detail-label">Back</div><div class="detail-value">' + escapeHtml(card.back) + '</div></div>' +
            '<div class="detail-item"><div class="detail-label">Tags</div><div class="detail-value">' + (tagsHtml || 'None') + '</div></div>' +
            '<div class="detail-item"><div class="detail-label">State</div><div class="detail-value">' + state + '</div></div>' +
            '<div class="detail-item"><div class="detail-label">Due</div><div class="detail-value">' + (card.due ? formatDate(card.due) : '-') + '</div></div>' +
            '<div class="detail-item"><div class="detail-label">Created</div><div class="detail-value">' + (card.createTime ? formatDate(card.createTime) : '-') + '</div></div>';

        window.manageModal.open('Card Detail', bodyHtml, null);
    }

    function showEditModal(card) {
        fetch('/api/tags', { credentials: 'same-origin' })
            .then(function (r) { return r.json(); })
            .then(function (allTags) {
                var currentTagIds = {};
                (card.tags || []).forEach(function (t) { currentTagIds[t.id] = true; });

                var tagCheckboxes = allTags.map(function (t) {
                    var checked = currentTagIds[t.id] ? ' checked' : '';
                    return '<div class="checkbox-row"><input type="checkbox" class="tag-cb" value="' + t.id + '"' + checked + '> ' + escapeHtml(t.name) + '</div>';
                }).join('');

                var bodyHtml =
                    '<input type="text" id="editFront" class="edit-front" value="' + escapeHtml(card.front) + '">' +
                    '<textarea id="editBack" class="edit-back" rows="2">' + escapeHtml(card.back) + '</textarea>' +
                    '<div style="max-height:200px;overflow-y:auto;margin-bottom:8px;">' + tagCheckboxes + '</div>';

                window.manageModal.open('Edit Card', bodyHtml, function (modalEl) {
                    var front = modalEl.querySelector('#editFront').value.trim();
                    var back = modalEl.querySelector('#editBack').value.trim();
                    if (!front || !back) { alert('Front and back are required'); return; }

                    var selectedTagIds = [];
                    modalEl.querySelectorAll('.tag-cb:checked').forEach(function (cb) {
                        selectedTagIds.push(cb.value);
                    });

                    fetch('/api/cards/' + card.id, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ front: front, back: back, tagIds: selectedTagIds }),
                        credentials: 'same-origin'
                    })
                        .then(function (resp) {
                            if (resp.ok) { window.manageModal.close(); loadCards(); return; }
                            return resp.json().then(function (body) { throw { message: body.message, status: resp.status }; });
                        })
                        .catch(function (err) {
                            if (err.status === 422) { alert(err.message); }
                            else { alert('保存失败'); }
                        });
                });
            });
    }

    function deleteCard(card) {
        if (!confirm('确定要删除卡片 "' + card.front + '" 吗？')) return;

        fetch('/api/cards/' + card.id, { method: 'DELETE', credentials: 'same-origin' })
            .then(function (resp) {
                if (resp.ok) { loadCards(); return; }
                alert('删除失败');
            });
    }

    function showCreate() {
        fetch('/api/tags', { credentials: 'same-origin' })
            .then(function (r) { return r.json(); })
            .then(function (allTags) {
                var tagCheckboxes = allTags.map(function (t) {
                    return '<div class="checkbox-row"><input type="checkbox" class="tag-cb" value="' + t.id + '"> ' + escapeHtml(t.name) + '</div>';
                }).join('');

                var bodyHtml =
                    '<input type="text" id="createFront" class="create-front" placeholder="单词或表达...">' +
                    '<textarea id="createBack" class="create-back" placeholder="释义..." rows="2"></textarea>' +
                    '<div style="max-height:200px;overflow-y:auto;margin-bottom:8px;">' + tagCheckboxes + '</div>';

                window.manageModal.open('Create Card', bodyHtml, function (modalEl) {
                    var front = modalEl.querySelector('#createFront').value.trim();
                    var back = modalEl.querySelector('#createBack').value.trim();
                    if (!front || !back) { alert('Front and back are required'); return; }

                    var selectedTagIds = [];
                    modalEl.querySelectorAll('.tag-cb:checked').forEach(function (cb) {
                        selectedTagIds.push(cb.value);
                    });

                    fetch('/api/cards/add', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ front: front, back: back, tagIds: selectedTagIds }),
                        credentials: 'same-origin'
                    })
                        .then(function (resp) {
                            if (resp.ok) { window.manageModal.close(); loadCards(); loadDecks(); return; }
                            return resp.json().then(function (body) { throw { message: body.message, status: resp.status }; });
                        })
                        .catch(function (err) {
                            if (err.status === 422) { alert(err.message); }
                            else { alert('创建失败'); }
                        });
                });
            });
    }

    function formatDate(dateStr) {
        try {
            return new Date(dateStr).toLocaleString();
        } catch (e) {
            return dateStr;
        }
    }

    function truncate(text, len) {
        if (!text) return '';
        if (text.length <= len) return text;
        return text.substring(0, len) + '...';
    }

    function escapeHtml(text) {
        if (!text) return '';
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    window.manageCards = {
        init: init,
        destroy: destroy,
        showCreate: showCreate
    };
})();
