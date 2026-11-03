package com.remoto.mobile;

/** Codigos de tecla do Java (java.awt.event.KeyEvent.VK_*), que e o que o computador remoto espera. */
final class KeyMap {
    private KeyMap() {}

    static final int VK_BACKSPACE = 8, VK_TAB = 9, VK_ENTER = 10, VK_SHIFT = 16, VK_CTRL = 17, VK_ALT = 18,
            VK_ESC = 27, VK_SPACE = 32, VK_PGUP = 33, VK_PGDN = 34, VK_END = 35, VK_HOME = 36,
            VK_LEFT = 37, VK_UP = 38, VK_RIGHT = 39, VK_DOWN = 40, VK_DELETE = 127, VK_WIN = 524;

    /**
     * Tecla direta para letras minusculas, digitos e espaco (iguais em qualquer layout de teclado).
     * Todo o resto (maiusculas, pontuacao, acentos) vai como texto, ver {@link Protocol#TEXT}.
     * @return codigo VK, ou -1 se o caractere deve ir como texto
     */
    static int direct(char c) {
        if (c >= 'a' && c <= 'z') return Character.toUpperCase(c);
        if (c >= '0' && c <= '9') return c;
        if (c == ' ') return VK_SPACE;
        return -1;
    }
}
