(function () {
    'use strict';

    var container = document.getElementById('tagsTab');

    function init() {
        loadTags();
    }

    function destroy() {
        container.innerHTML = '<div class="empty-state">暂无标签，创建标签</div>';
    }

    function loadTags() {
        fetch('/api/tags', { credentials: 'same-origin' })
            .then(function (r) { return r.json(); })
            .then(function (tags) {
                renderTags(tags);
            })
            .catch(function () {
                container.innerHTML = '<div class="empty-state">加载失败</div>';
            });
    }

    function renderTags(tags) {
        if (tags.length === 0) {
            container.innerHTML =
                '<div class="empty-state">暂无标签' +
                    '<br><button class="btn btn-primary" onclick="window.manageTags.showCreate()">创建标签</button>' +
                '</div>';
            return;
        }

        var html = '<table class="tag-table"><thead><tr>' +
            '<th>Name</th><th>Deck</th><th></th></tr></thead><tbody>';

        for (var i = 0; i < tags.length; i++) {
            var t = tags[i];
            var isDeck = t.type === 'deck' ? ' checked' : '';
            html += '<tr data-id="' + t.id + '" data-name="' + escapeHtml(t.name) + '" data-type="' + (t.type || '') + '">' +
                '<td class="tag-name">' + escapeHtml(t.name) + '</td>' +
                '<td><input type="checkbox" class="tag-deck-cb" disabled' + isDeck + '></td>' +
                '<td class="tag-actions">' +
                    '<button class="btn-edit">Edit</button>' +
                    '<button class="btn-delete">Delete</button>' +
                '</td>' +
                '</tr>';
        }
        html += '</tbody></table>';

        html += '<div style="margin-top:12px;text-align:center;">' +
            '<button class="btn btn-primary" onclick="window.manageTags.showCreate()">创建标签</button></div>';

        container.innerHTML = html;

        container.querySelectorAll('.btn-edit').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var row = this.closest('tr');
                startEdit(row);
            });
        });

        container.querySelectorAll('.btn-delete').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var row = this.closest('tr');
                deleteTag(row);
            });
        });
    }

    function startEdit(row) {
        var id = row.getAttribute('data-id');
        var name = row.getAttribute('data-name');
        var type = row.getAttribute('data-type');

        row.innerHTML =
            '<td><input type="text" class="edit-name-input" value="' + escapeHtml(name) + '"></td>' +
            '<td><input type="checkbox" class="edit-deck-cb"' + (type === 'deck' ? ' checked' : '') + '></td>' +
            '<td class="tag-actions">' +
                '<button class="btn-save">Save</button>' +
                '<button class="btn-cancel">Cancel</button>' +
            '</td>';

        row.querySelector('.btn-save').addEventListener('click', function () {
            var newName = row.querySelector('.edit-name-input').value.trim();
            var isDeck = row.querySelector('.edit-deck-cb').checked;
            saveEdit(id, newName, isDeck ? 'deck' : null, row);
        });

        row.querySelector('.btn-cancel').addEventListener('click', function () {
            loadTags();
        });
    }

    function saveEdit(id, name, type, row) {
        fetch('/api/tags/' + id, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: name, type: type }),
            credentials: 'same-origin'
        })
            .then(function (resp) {
                if (resp.ok) {
                    loadTags();
                    return;
                }
                return resp.json().then(function (body) {
                    throw { message: (body && body.message) || 'Update failed', status: resp.status };
                });
            })
            .catch(function (err) {
                if (err.status === 422) {
                    alert(err.message);
                } else {
                    alert('保存失败: ' + (err.message || 'Unknown error'));
                }
            });
    }

    function deleteTag(row) {
        var id = row.getAttribute('data-id');
        var name = row.getAttribute('data-name');

        if (!confirm('确定要删除标签 "' + name + '" 吗？')) return;

        fetch('/api/tags/' + id, {
            method: 'DELETE',
            credentials: 'same-origin'
        })
            .then(function (resp) {
                if (resp.ok) {
                    loadTags();
                    return;
                }
                if (resp.status === 422) {
                    return resp.json().then(function (errorBody) {
                        var orphanCount = '?';
                        try {
                            var msg = errorBody.message || '';
                            var parsed = JSON.parse(msg);
                            orphanCount = parsed.orphanCount;
                        } catch (e) {}
                        alert(orphanCount + ' 张卡片将失去所有牌组，无法删除');
                        throw { handled: true };
                    });
                }
                return resp.json().then(function (body) {
                    throw { message: (body && body.message) || 'Delete failed', status: resp.status };
                });
            })
            .catch(function (err) {
                if (err.handled) return;
                alert('删除失败: ' + (err.message || 'Unknown error'));
            });
    }

    function showCreate() {
        window.manageModal.open('创建标签',
            '<div class="checkbox-row" style="margin-top:8px;">' +
                '<input type="checkbox" id="newTagDeck" class="new-tag-deck"><label for="newTagDeck">作为牌组</label>' +
            '</div>' +
            '<input type="text" id="newTagName" class="new-tag-name" placeholder="标签名称">',
            function (modalEl) {
                var name = modalEl.querySelector('#newTagName').value.trim();
                var isDeck = modalEl.querySelector('#newTagDeck').checked;
                if (!name) {
                    alert('标签名不能为空');
                    return;
                }
                fetch('/api/tags', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name: name, type: isDeck ? 'deck' : null }),
                    credentials: 'same-origin'
                })
                    .then(function (resp) {
                        if (resp.ok) {
                            window.manageModal.close();
                            loadTags();
                            return;
                        }
                        return resp.json().then(function (body) {
                            throw { message: (body && body.message) || 'Create failed', status: resp.status };
                        });
                    })
                    .catch(function (err) {
                        if (err.status === 422) {
                            alert(err.message);
                        } else {
                            alert('创建失败: ' + (err.message || 'Unknown error'));
                        }
                    });
            }
        );
    }

    function escapeHtml(text) {
        if (!text) return '';
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    window.manageTags = {
        init: init,
        destroy: destroy,
        showCreate: showCreate
    };
})();
