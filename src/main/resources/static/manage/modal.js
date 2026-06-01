(function () {
    'use strict';

    var currentModal = null;

    function createModal(title, bodyHtml, onSave) {
        if (currentModal) {
            currentModal.remove();
        }

        var overlay = document.createElement('div');
        overlay.className = 'modal';
        overlay.innerHTML =
            '<div class="modal-content">' +
                '<h2>' + escapeHtml(title) + '</h2>' +
                '<div class="modal-body">' + bodyHtml + '</div>' +
                '<div class="modal-actions">' +
                    '<button class="btn btn-cancel">Cancel</button>' +
                    '<button class="btn btn-primary btn-save">Save</button>' +
                '</div>' +
            '</div>';

        overlay.querySelector('.btn-cancel').addEventListener('click', function () {
            closeModal();
        });

        overlay.querySelector('.btn-save').addEventListener('click', function () {
            var modalEl = overlay.querySelector('.modal-content');
            if (onSave) {
                onSave(modalEl);
            }
        });

        overlay.addEventListener('click', function (e) {
            if (e.target === overlay) {
                closeModal();
            }
        });

        document.body.appendChild(overlay);
        currentModal = overlay;
    }

    function closeModal() {
        if (currentModal) {
            currentModal.remove();
            currentModal = null;
        }
    }

    function escapeHtml(text) {
        if (!text) return '';
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    window.manageModal = {
        open: createModal,
        close: closeModal
    };
})();
