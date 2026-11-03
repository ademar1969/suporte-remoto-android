package com.remoto.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mostra a tela do computador remoto e converte toques em mouse/teclado.
 * Um dedo move o cursor, toque clica, toque longo = botao direito, dois dedos rolam a pagina.
 * Pinca com dois dedos aproxima/afasta; com zoom, arrastar dois dedos move a imagem
 * (ao chegar na borda da imagem, o movimento vertical que sobra rola a pagina).
 */
final class RemoteView extends View {

    interface Listener { void onModifiersChanged(); }

    private final Channel ch;
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final AtomicReference<float[]> pendingMove = new AtomicReference<>();
    private final AtomicBoolean moveQueued = new AtomicBoolean(false);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect dst = new Rect();
    private final int touchSlop;
    private final float scrollStep;

    // cursor desenhado no celular (a imagem enviada pelo computador nao inclui a seta do mouse)
    private final Path cursorPath = new Path();
    private final Paint cursorFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Ultima posicao enviada, normalizada 0..1; negativa = ainda nao tocou. */
    private volatile float cursorNX = -1, cursorNY = -1;

    // zoom: 1 = tela inteira; offX/offY deslocam a imagem ampliada (pixels da view)
    private static final float MAX_ZOOM = 5f;
    private float zoom = 1f, offX, offY;
    private final Rect base = new Rect();
    /** Gesto de dois dedos: ainda indefinido, pinca (zoom), arrastar imagem ou rolar. */
    private static final int TWO_NONE = 0, TWO_PINCH = 1, TWO_PAN = 2, TWO_SCROLL = 3;
    private int twoMode;
    private float twoStartSpan, twoStartMidX, twoStartMidY, twoLastMidX, twoLastMidY, twoLastSpan;

    private volatile Bitmap frame;
    private Listener listener;

    // modificadores "grudentos": valem para a proxima tecla
    boolean ctrl, alt, shift, win;
    /** No modo arrastar, o dedo segura o botao esquerdo (selecionar texto, mover janelas). */
    boolean dragMode;

    // estado do gesto
    private float downX, downY, scrollAccum;
    private boolean moved, longPressed, scrolling, pressedByDrag;
    private long downTime;
    // duplo clique: o 2o toque rapido e proximo reaproveita o ponto exato do 1o (o Windows exige isso)
    private static final long DOUBLE_TAP_MS = 450;
    private float tapX, tapY, lastTapX, lastTapY;
    private long lastTapTime;
    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (!moved && !scrolling) {
                longPressed = true;
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                click(3);
            }
        }
    };

    RemoteView(Context ctx, Channel ch) {
        super(ctx);
        this.ch = ch;
        setFocusable(true);
        setFocusableInTouchMode(true);
        touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        float density = ctx.getResources().getDisplayMetrics().density;
        scrollStep = 36 * density;
        textPaint.setColor(Color.LTGRAY);
        textPaint.setTextSize(18 * density);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // seta classica do mouse, com a ponta em (0,0); ~20dp de altura
        float u = density * 1.25f;
        cursorPath.moveTo(0, 0);
        cursorPath.lineTo(0, 16 * u);
        cursorPath.lineTo(4 * u, 12.5f * u);
        cursorPath.lineTo(7 * u, 19 * u);
        cursorPath.lineTo(9.5f * u, 18 * u);
        cursorPath.lineTo(6.5f * u, 11.5f * u);
        cursorPath.lineTo(11.5f * u, 11.5f * u);
        cursorPath.close();
        cursorFill.setColor(Color.WHITE);
        cursorFill.setStyle(Paint.Style.FILL);
        cursorStroke.setColor(Color.BLACK);
        cursorStroke.setStyle(Paint.Style.STROKE);
        cursorStroke.setStrokeWidth(1.5f * density);
        cursorStroke.setStrokeJoin(Paint.Join.MITER);
    }

    void setListener(Listener l) { listener = l; }

    void setFrame(Bitmap b) {
        frame = b;
        postInvalidate();
    }

    void shutdown() {
        sender.shutdownNow();
    }

    // ------------------------------------------------------------------ desenho

    @Override protected void onDraw(Canvas c) {
        c.drawColor(Color.BLACK);
        Bitmap b = frame;
        if (b == null) {
            c.drawText("Aguardando a imagem do computador...", getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }
        layout(b, dst);
        c.drawBitmap(b, null, dst, paint);

        float nx = cursorNX, ny = cursorNY;
        if (nx >= 0 && ny >= 0) {
            float px = dst.left + nx * dst.width();
            float py = dst.top + ny * dst.height();
            c.save();
            c.translate(px, py);
            c.drawPath(cursorPath, cursorFill);
            c.drawPath(cursorPath, cursorStroke);
            c.restore();
        }
    }

    /** Retangulo onde a imagem e desenhada, ja com zoom e deslocamento (limitados as bordas). */
    private void layout(Bitmap b, Rect out) {
        int pw = getWidth(), ph = getHeight();
        fit(pw, ph, b, base);
        if (zoom <= 1.001f) {
            zoom = 1f;
            offX = offY = 0;
            out.set(base);
            return;
        }
        float w = base.width(), h = base.height();
        float zw = w * zoom, zh = h * zoom;
        float left = base.left - (zw - w) / 2f + offX;
        float top = base.top - (zh - h) / 2f + offY;
        left = zw > pw ? Math.max(pw - zw, Math.min(0, left)) : (pw - zw) / 2f;
        top = zh > ph ? Math.max(ph - zh, Math.min(0, top)) : (ph - zh) / 2f;
        offX = left - base.left + (zw - w) / 2f;
        offY = top - base.top + (zh - h) / 2f;
        out.set(Math.round(left), Math.round(top), Math.round(left + zw), Math.round(top + zh));
    }

    /** Volta para a tela inteira (botao "Ajustar"). */
    void resetZoom() {
        zoom = 1f;
        offX = offY = 0;
        invalidate();
    }

    /** Aplica a pinca: o ponto sob (oldMid) passa a ficar sob (newMid), com o novo zoom. */
    private void zoomAround(float oldMidX, float oldMidY, float newMidX, float newMidY, float factor) {
        Bitmap b = frame;
        if (b == null) return;
        layout(b, dst);
        float u = (oldMidX - dst.left) / (float) Math.max(1, dst.width());
        float v = (oldMidY - dst.top) / (float) Math.max(1, dst.height());
        float nz = Math.max(1f, Math.min(MAX_ZOOM, zoom * factor));
        float w = base.width(), h = base.height();
        float zw = w * nz, zh = h * nz;
        float left = newMidX - u * zw;
        float top = newMidY - v * zh;
        zoom = nz;
        offX = left - base.left + (zw - w) / 2f;
        offY = top - base.top + (zh - h) / 2f;
        layout(b, dst); // limita as bordas
        invalidate();
    }

    /** Arrasta a imagem ampliada; devolve o movimento vertical que nao coube (vira rolagem). */
    private float pan(float dx, float dy) {
        Bitmap b = frame;
        if (b == null) return dy;
        float before = offY;
        offX += dx;
        offY += dy;
        layout(b, dst);
        invalidate();
        return dy - (offY - before);
    }

    private static void fit(int pw, int ph, Bitmap b, Rect out) {
        double sc = Math.min((double) pw / b.getWidth(), (double) ph / b.getHeight());
        int w = Math.max(1, (int) (b.getWidth() * sc));
        int h = Math.max(1, (int) (b.getHeight() * sc));
        int x = (pw - w) / 2;
        int y = (ph - h) / 2;
        out.set(x, y, x + w, y + h);
    }

    // ------------------------------------------------------------------ envio (sempre fora da thread de UI)

    private void post(final int type, final byte[] payload) {
        try {
            sender.execute(new Runnable() {
                @Override public void run() {
                    try { ch.send(type, payload); } catch (IOException ignored) { }
                }
            });
        } catch (RuntimeException ignored) {
            // executor encerrado: a sessao ja terminou
        }
    }

    /** Move o cursor; movimentos acumulados sao reduzidos ao ultimo para nao encher a fila. */
    private void sendMove(float x, float y) {
        Bitmap b = frame;
        if (b == null) return;
        layout(b, dst);
        float nx = clamp((x - dst.left) / (float) Math.max(1, dst.width()));
        float ny = clamp((y - dst.top) / (float) Math.max(1, dst.height()));
        cursorNX = nx;
        cursorNY = ny;
        invalidate();
        pendingMove.set(new float[]{nx, ny});
        if (moveQueued.compareAndSet(false, true)) {
            try {
                sender.execute(new Runnable() {
                    @Override public void run() {
                        moveQueued.set(false);
                        float[] m = pendingMove.getAndSet(null);
                        if (m == null) return;
                        try { ch.send(Protocol.MOUSE_MOVE, Channel.floatBytes(m[0], m[1])); } catch (IOException ignored) { }
                    }
                });
            } catch (RuntimeException ignored) {
                moveQueued.set(false);
            }
        }
    }

    private static float clamp(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    private void press(int button) { post(Protocol.MOUSE_PRESS, Channel.intBytes(button)); }

    private void release(int button) { post(Protocol.MOUSE_RELEASE, Channel.intBytes(button)); }

    private void click(int button) {
        press(button);
        release(button);
    }

    // ------------------------------------------------------------------ toque

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                downTime = e.getEventTime();
                moved = false;
                longPressed = false;
                scrolling = false;
                tapX = downX;
                tapY = downY;
                if (!dragMode && downTime - lastTapTime < DOUBLE_TAP_MS
                        && Math.hypot(downX - lastTapX, downY - lastTapY) < touchSlop * 3) {
                    tapX = lastTapX;
                    tapY = lastTapY;
                }
                sendMove(tapX, tapY);
                if (dragMode) {
                    press(1);
                    pressedByDrag = true;
                } else {
                    ui.postDelayed(longPress, 600);
                }
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2) {
                    ui.removeCallbacks(longPress);
                    if (pressedByDrag) {
                        release(1);
                        pressedByDrag = false;
                    }
                    scrolling = true;
                    scrollAccum = 0;
                    twoMode = TWO_NONE;
                    twoStartSpan = twoLastSpan = span(e);
                    twoStartMidX = twoLastMidX = (e.getX(0) + e.getX(1)) / 2f;
                    twoStartMidY = twoLastMidY = (e.getY(0) + e.getY(1)) / 2f;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (scrolling && e.getPointerCount() >= 2) {
                    twoFingerMove(e);
                } else if (!scrolling) {
                    if (!moved && Math.hypot(e.getX() - downX, e.getY() - downY) > touchSlop) {
                        moved = true;
                        ui.removeCallbacks(longPress);
                    }
                    if (moved || dragMode) sendMove(e.getX(), e.getY());
                }
                return true;

            case MotionEvent.ACTION_UP:
                ui.removeCallbacks(longPress);
                if (pressedByDrag) {
                    sendMove(e.getX(), e.getY());
                    release(1);
                    pressedByDrag = false;
                } else if (!scrolling && !moved && !longPressed && e.getEventTime() - downTime < 600) {
                    click(1);
                    lastTapX = tapX;
                    lastTapY = tapY;
                    lastTapTime = e.getEventTime();
                }
                scrolling = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                ui.removeCallbacks(longPress);
                if (pressedByDrag) {
                    release(1);
                    pressedByDrag = false;
                }
                scrolling = false;
                return true;

            default:
                return super.onTouchEvent(e);
        }
    }

    private static float span(MotionEvent e) {
        return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
    }

    private void twoFingerMove(MotionEvent e) {
        float sp = span(e);
        float mx = (e.getX(0) + e.getX(1)) / 2f;
        float my = (e.getY(0) + e.getY(1)) / 2f;

        if (twoMode == TWO_NONE) {
            // decide o gesto pelo que mudou primeiro: distancia entre os dedos ou posicao deles
            float limit = touchSlop * 1.5f;
            if (Math.abs(sp - twoStartSpan) > limit) {
                twoMode = TWO_PINCH;
            } else if (Math.hypot(mx - twoStartMidX, my - twoStartMidY) > touchSlop) {
                twoMode = zoom > 1.001f ? TWO_PAN : TWO_SCROLL;
            } else {
                return;
            }
            twoLastSpan = sp;
            twoLastMidX = mx;
            twoLastMidY = my;
            return;
        }

        float dy = my - twoLastMidY;
        if (twoMode == TWO_PINCH) {
            if (twoLastSpan > 0) zoomAround(twoLastMidX, twoLastMidY, mx, my, sp / twoLastSpan);
        } else if (twoMode == TWO_PAN) {
            scrollBy(pan(mx - twoLastMidX, dy));
        } else {
            scrollBy(dy);
        }
        twoLastSpan = sp;
        twoLastMidX = mx;
        twoLastMidY = my;
    }

    /** Acumula movimento vertical e manda a roda do mouse a cada passo. */
    private void scrollBy(float dy) {
        scrollAccum += dy;
        while (Math.abs(scrollAccum) >= scrollStep) {
            // dedos para cima = pagina desce (roda positiva)
            post(Protocol.MOUSE_WHEEL, Channel.intBytes(scrollAccum < 0 ? 1 : -1));
            scrollAccum += scrollAccum < 0 ? scrollStep : -scrollStep;
        }
    }

    // ------------------------------------------------------------------ teclado

    /** Toca uma tecla (VK do Java) aplicando os modificadores marcados, que depois sao soltos. */
    void tapKey(int vk) {
        if (ctrl) post(Protocol.KEY_PRESS, Channel.intBytes(KeyMap.VK_CTRL));
        if (alt) post(Protocol.KEY_PRESS, Channel.intBytes(KeyMap.VK_ALT));
        if (shift) post(Protocol.KEY_PRESS, Channel.intBytes(KeyMap.VK_SHIFT));
        if (win) post(Protocol.KEY_PRESS, Channel.intBytes(KeyMap.VK_WIN));
        post(Protocol.KEY_PRESS, Channel.intBytes(vk));
        post(Protocol.KEY_RELEASE, Channel.intBytes(vk));
        if (win) post(Protocol.KEY_RELEASE, Channel.intBytes(KeyMap.VK_WIN));
        if (shift) post(Protocol.KEY_RELEASE, Channel.intBytes(KeyMap.VK_SHIFT));
        if (alt) post(Protocol.KEY_RELEASE, Channel.intBytes(KeyMap.VK_ALT));
        if (ctrl) post(Protocol.KEY_RELEASE, Channel.intBytes(KeyMap.VK_CTRL));
        if (ctrl || alt || shift || win) {
            ctrl = alt = shift = win = false;
            if (listener != null) listener.onModifiersChanged();
        }
    }

    /** Digita texto: letras minusculas, numeros e espaco como teclas; o resto (acentos, maiusculas, simbolos) como texto. */
    void typeText(CharSequence t) {
        StringBuilder other = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            int vk = c == '\n' ? KeyMap.VK_ENTER : KeyMap.direct(c);
            if (vk < 0) {
                other.append(c);
                continue;
            }
            flushText(other);
            tapKey(vk);
        }
        flushText(other);
    }

    private void flushText(StringBuilder sb) {
        if (sb.length() == 0) return;
        post(Protocol.TEXT, sb.toString().getBytes(StandardCharsets.UTF_8));
        sb.setLength(0);
    }

    @Override public boolean onCheckIsTextEditor() { return true; }

    @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        return new BaseInputConnection(this, false) {
            @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                typeText(text);
                return true;
            }

            @Override public boolean deleteSurroundingText(int before, int after) {
                for (int i = 0; i < Math.max(1, before); i++) tapKey(KeyMap.VK_BACKSPACE);
                return true;
            }
        };
    }

    private static int vkFromAndroid(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL: return KeyMap.VK_BACKSPACE;
            case KeyEvent.KEYCODE_FORWARD_DEL: return KeyMap.VK_DELETE;
            case KeyEvent.KEYCODE_TAB: return KeyMap.VK_TAB;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return KeyMap.VK_ENTER;
            case KeyEvent.KEYCODE_ESCAPE: return KeyMap.VK_ESC;
            case KeyEvent.KEYCODE_DPAD_LEFT: return KeyMap.VK_LEFT;
            case KeyEvent.KEYCODE_DPAD_UP: return KeyMap.VK_UP;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return KeyMap.VK_RIGHT;
            case KeyEvent.KEYCODE_DPAD_DOWN: return KeyMap.VK_DOWN;
            case KeyEvent.KEYCODE_MOVE_HOME: return KeyMap.VK_HOME;
            case KeyEvent.KEYCODE_MOVE_END: return KeyMap.VK_END;
            case KeyEvent.KEYCODE_PAGE_UP: return KeyMap.VK_PGUP;
            case KeyEvent.KEYCODE_PAGE_DOWN: return KeyMap.VK_PGDN;
            default: return -1;
        }
    }

    /** Teclas do teclado virtual (Backspace, Enter...) e de teclado fisico. */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyDown(keyCode, event);
        }
        int vk = vkFromAndroid(keyCode);
        if (vk >= 0) {
            tapKey(vk);
            return true;
        }
        int u = event.getUnicodeChar();
        if (u > 0 && !event.isCtrlPressed()) {
            typeText(new String(Character.toChars(u)));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
