(function () {
    'use strict';

    function initNav() {
        var header = document.querySelector('header');
        if (!header) return;

        var showToken = header.getAttribute('data-show-token') !== 'false';

        header.innerHTML = '';

        var logoutForm = document.createElement('form');
        logoutForm.action = '/logout';
        logoutForm.method = 'post';
        logoutForm.className = 'logout-form';
        var logoutBtn = document.createElement('button');
        logoutBtn.type = 'submit';
        logoutBtn.className = 'btn-logout';
        logoutBtn.textContent = 'Logout';
        logoutForm.appendChild(logoutBtn);
        header.appendChild(logoutForm);

        if (showToken) {
            var tokenContainer = document.createElement('div');
            tokenContainer.className = 'token-bar-container';
            tokenContainer.innerHTML =
                '<div class="token-bar-label">Token</div>' +
                '<div class="token-bar">' +
                    '<div class="token-bar-fill" id="tokenBar"></div>' +
                '</div>' +
                '<div class="token-bar-pct" id="tokenPct">0%</div>';
            header.appendChild(tokenContainer);
        }

        var menuBtn = document.createElement('button');
        menuBtn.className = 'nav-menu-btn';
        menuBtn.textContent = '\u2630';
        menuBtn.addEventListener('click', function () {
            toggleSidebar();
        });
        header.appendChild(menuBtn);

        createSidebar();
    }

    function createSidebar() {
        if (document.getElementById('navSidebar')) return;

        var sidebar = document.createElement('div');
        sidebar.id = 'navSidebar';
        sidebar.className = 'nav-sidebar';

        var headerDiv = document.createElement('div');
        headerDiv.className = 'nav-sidebar-header';
        var titleSpan = document.createElement('span');
        titleSpan.textContent = 'Menu';
        var closeBtn = document.createElement('button');
        closeBtn.textContent = '\u00d7';
        closeBtn.addEventListener('click', function () {
            closeSidebar();
        });
        headerDiv.appendChild(titleSpan);
        headerDiv.appendChild(closeBtn);
        sidebar.appendChild(headerDiv);

        var linksDiv = document.createElement('div');
        linksDiv.className = 'nav-sidebar-links';

        var currentPath = window.location.pathname;
        var isChatPage = currentPath === '/' || currentPath === '/index.html' || currentPath === '';
        var isManagePage = currentPath.indexOf('/manage/') === 0;

        var chatLink = document.createElement('a');
        chatLink.className = 'nav-link' + (isChatPage ? ' active' : '');
        chatLink.href = '/';
        chatLink.textContent = '💬 Chat';
        linksDiv.appendChild(chatLink);

        var cardsLink = document.createElement('a');
        cardsLink.className = 'nav-link' + (isManagePage ? ' active' : '');
        cardsLink.href = '/manage/index.html';
        cardsLink.textContent = '📋 Manage';
        linksDiv.appendChild(cardsLink);

        sidebar.appendChild(linksDiv);
        document.body.appendChild(sidebar);
    }

    function toggleSidebar() {
        var sidebar = document.getElementById('navSidebar');
        if (!sidebar) return;
        var isOpen = sidebar.classList.contains('open');
        if (isOpen) {
            closeSidebar();
        } else {
            openSidebar();
        }
    }

    function openSidebar() {
        var sidebar = document.getElementById('navSidebar');
        if (!sidebar) return;
        sidebar.classList.add('open');

        var correctionSidebar = document.getElementById('correctionSidebar');
        if (correctionSidebar) {
            correctionSidebar.classList.add('collapsed');
        }
    }

    function closeSidebar() {
        var sidebar = document.getElementById('navSidebar');
        if (!sidebar) return;
        sidebar.classList.remove('open');
    }

    window.openNav = openSidebar;
    window.closeNav = closeSidebar;

    window.updateTokenBar = function (percent) {
        var bar = document.getElementById('tokenBar');
        var pct = document.getElementById('tokenPct');
        if (bar) bar.style.width = percent + '%';
        if (pct) pct.textContent = percent + '%';
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initNav);
    } else {
        initNav();
    }
})();
