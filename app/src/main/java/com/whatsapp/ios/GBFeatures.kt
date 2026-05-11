package com.whatsapp.ios

object GBFeatures {

    // ── iOS Light Theme CSS ────────────────────────────────────
    val IOS_THEME_CSS = """
        /* === iOS WhatsApp Light Theme === */
        
        /* Background */
        #main { background-color: #F2F2F7 !important; }
        body { background-color: #FFFFFF !important; }
        
        /* Header - iOS style */
        header { 
            background-color: #F2F2F7 !important;
            backdrop-filter: blur(20px) !important;
            -webkit-backdrop-filter: blur(20px) !important;
            border-bottom: 0.5px solid #C6C6C8 !important;
        }
        
        /* Chat list */
        #pane-side {
            background-color: #FFFFFF !important;
        }
        
        /* Chat list items */
        ._ak8l, [data-testid="cell-frame-container"] {
            background-color: #FFFFFF !important;
            border-bottom: 0.5px solid #F2F2F7 !important;
        }
        ._ak8l:active, [data-testid="cell-frame-container"]:active {
            background-color: #D1D1D6 !important;
        }
        
        /* Message bubbles - iOS style */
        .message-out .copyable-text,
        .message-out ._ao3e {
            background-color: #34C759 !important;
            border-radius: 18px 18px 4px 18px !important;
            color: #FFFFFF !important;
        }
        .message-in .copyable-text,
        .message-in ._ao3e {
            background-color: #FFFFFF !important;
            border-radius: 18px 18px 18px 4px !important;
            color: #000000 !important;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1) !important;
        }
        
        /* Chat background */
        #main .copyable-area {
            background-color: #F2F2F7 !important;
            background-image: none !important;
        }
        
        /* Input area - iOS style */
        footer {
            background-color: #F2F2F7 !important;
            border-top: 0.5px solid #C6C6C8 !important;
        }
        [data-testid="conversation-compose-box-input"] {
            background-color: #FFFFFF !important;
            border-radius: 20px !important;
            border: 1px solid #C6C6C8 !important;
            color: #000000 !important;
        }
        
        /* iOS font */
        * { font-family: -apple-system, 'SF Pro Text', 'Helvetica Neue', sans-serif !important; }
        
        /* Send button - iOS green */
        [data-testid="send"] {
            background-color: #34C759 !important;
            border-radius: 50% !important;
        }
        
        /* Ticks - iOS blue */
        [data-testid="msg-dblcheck"] { color: #007AFF !important; }
        
        /* Links - iOS blue */
        a { color: #007AFF !important; }
        
        /* Search bar - iOS style */
        [data-testid="search"] {
            background-color: #E5E5EA !important;
            border-radius: 10px !important;
        }
        
        /* Avatar circles */
        [data-testid="default-user"] {
            background-color: #8E8E93 !important;
        }
        
        /* Remove scrollbar */
        ::-webkit-scrollbar { display: none !important; }
        
        /* iOS safe area padding */
        #app { padding-bottom: env(safe-area-inset-bottom) !important; }
        
        /* Timestamps */
        ._akbu { color: #8E8E93 !important; font-size: 11px !important; }
        
        /* Unread badge - iOS red */
        [data-testid="icon-unread-count"] {
            background-color: #FF3B30 !important;
            border-radius: 10px !important;
        }
        
        /* Status bar area */
        ._ak8q { background-color: #34C759 !important; }
    """.trimIndent()

    // ── Anti-Delete JS ─────────────────────────────────────────
    // Messages delete වෙද්දී catch කරලා save කරනවා
    val ANTI_DELETE_JS = """
        (function() {
            'use strict';
            
            const deletedMessages = new Map();
            const STORAGE_KEY = 'wa_anti_delete';
            
            // Load saved messages
            try {
                const saved = localStorage.getItem(STORAGE_KEY);
                if (saved) {
                    const arr = JSON.parse(saved);
                    arr.forEach(([k,v]) => deletedMessages.set(k, v));
                }
            } catch(e) {}
            
            // Save to localStorage
            function saveMessages() {
                try {
                    const arr = Array.from(deletedMessages.entries()).slice(-500);
                    localStorage.setItem(STORAGE_KEY, JSON.stringify(arr));
                } catch(e) {}
            }
            
            // Observe DOM for message changes
            const observer = new MutationObserver(function(mutations) {
                mutations.forEach(function(mutation) {
                    // Save new messages
                    mutation.addedNodes.forEach(function(node) {
                        if (node.nodeType === 1) {
                            const msgEl = node.querySelector
                                ? node.querySelector('[data-id]') || node
                                : null;
                            if (msgEl && msgEl.dataset && msgEl.dataset.id) {
                                const id = msgEl.dataset.id;
                                const textEl = msgEl.querySelector('.copyable-text, ._ao3e');
                                if (textEl && textEl.innerText) {
                                    deletedMessages.set(id, {
                                        text: textEl.innerText,
                                        time: new Date().toLocaleTimeString(),
                                        timestamp: Date.now()
                                    });
                                    saveMessages();
                                }
                            }
                        }
                    });
                    
                    // Detect deleted messages
                    mutation.removedNodes.forEach(function(node) {
                        if (node.nodeType === 1) {
                            const msgEl = node.querySelector
                                ? node.querySelector('[data-id]') || node
                                : null;
                            if (msgEl && msgEl.dataset && msgEl.dataset.id) {
                                const id = msgEl.dataset.id;
                                const saved = deletedMessages.get(id);
                                if (saved) {
                                    console.log('[Anti-Delete] Message deleted:', saved.text);
                                    showDeletedNotification(saved);
                                }
                            }
                        }
                    });
                });
            });
            
            // Start observing
            function startObserving() {
                const target = document.getElementById('main') || document.body;
                observer.observe(target, {
                    childList: true,
                    subtree: true
                });
                console.log('[GB WhatsApp] Anti-delete enabled ✅');
            }
            
            // Show deleted message notification
            function showDeletedNotification(msg) {
                const notif = document.createElement('div');
                notif.style.cssText = `
                    position: fixed;
                    bottom: 80px;
                    left: 50%;
                    transform: translateX(-50%);
                    background: rgba(0,0,0,0.85);
                    color: white;
                    padding: 10px 16px;
                    border-radius: 20px;
                    font-size: 13px;
                    z-index: 99999;
                    max-width: 80vw;
                    text-align: center;
                    backdrop-filter: blur(10px);
                    -webkit-backdrop-filter: blur(10px);
                    animation: fadeInOut 3s forwards;
                `;
                notif.innerHTML = '🗑️ Deleted: "' + 
                    msg.text.substring(0, 50) + (msg.text.length > 50 ? '...' : '') + '"';
                
                const style = document.createElement('style');
                style.textContent = '@keyframes fadeInOut{0%{opacity:0;transform:translateX(-50%) translateY(10px)}15%{opacity:1;transform:translateX(-50%) translateY(0)}85%{opacity:1}100%{opacity:0}}';
                document.head.appendChild(style);
                document.body.appendChild(notif);
                setTimeout(() => notif.remove(), 3000);
            }
            
            // Wait for WhatsApp to load
            if (document.readyState === 'complete') {
                setTimeout(startObserving, 2000);
            } else {
                window.addEventListener('load', () => setTimeout(startObserving, 2000));
            }
            
        })();
    """.trimIndent()

    // ── Hide Typing Indicator JS ───────────────────────────────
    val HIDE_TYPING_JS = """
        (function() {
            // Override typing status broadcast
            const origSend = WebSocket.prototype.send;
            WebSocket.prototype.send = function(data) {
                return origSend.apply(this, arguments);
            };
        })();
    """.trimIndent()

    // ── iOS Haptic feedback simulation ────────────────────────
    val IOS_HAPTIC_JS = """
        (function() {
            document.addEventListener('click', function(e) {
                if (navigator.vibrate) navigator.vibrate(10);
            });
        })();
    """.trimIndent()

    // ── Blue ticks hide attempt JS ─────────────────────────────
    val BLUE_TICK_CSS = """
        /* Attempt to keep ticks grey (server controls actual read receipts) */
        [data-testid="msg-dblcheck"] svg path { fill: #92A3A7 !important; }
        [data-testid="msg-check"] svg path    { fill: #92A3A7 !important; }
    """.trimIndent()

    // ── Full injection script ──────────────────────────────────
    fun buildInjectionScript(
        antiDelete: Boolean,
        iosTheme: Boolean,
        hideBlueCheck: Boolean,
    ): String {
        val parts = mutableListOf<String>()

        if (iosTheme) {
            parts.add("""
                (function() {
                    var s = document.createElement('style');
                    s.id = 'gb-ios-theme';
                    s.textContent = `${IOS_THEME_CSS.replace("`", "'")}`;
                    document.head.appendChild(s);
                    console.log('[GB WhatsApp] iOS theme applied ✅');
                })();
            """.trimIndent())
        }

        if (hideBlueCheck) {
            parts.add("""
                (function() {
                    var s = document.createElement('style');
                    s.id = 'gb-blue-tick';
                    s.textContent = `${BLUE_TICK_CSS.replace("`", "'")}`;
                    document.head.appendChild(s);
                })();
            """.trimIndent())
        }

        if (antiDelete) {
            parts.add(ANTI_DELETE_JS)
        }

        parts.add(IOS_HAPTIC_JS)

        return parts.joinToString("\n\n")
    }
}
