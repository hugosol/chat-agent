(function () {
    'use strict';

    if (window.location.search.includes('error')) {
        var errorEl = document.getElementById('errorMsg');
        if (errorEl) {
            errorEl.classList.remove('hidden');
        }
    }
})();
