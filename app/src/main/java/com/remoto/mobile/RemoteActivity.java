package com.remoto.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.IOException;

/** Tela de controle remoto: imagem do computador + barra de teclas. */
public class RemoteActivity extends Activity {

    private RemoteView view;
    private Channel ch;
    private Button ctrlBtn, altBtn, shiftBtn, winBtn, dragBtn;
    private volatile boolean leaving;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ch = Session.channel;
        if (ch == null) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTitle(Session.hostName);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        view = new RemoteView(this, ch);
        view.setListener(new RemoteView.Listener() {
            @Override public void onModifiersChanged() { refreshToggles(); }
        });
        root.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        view.requestFocus();

        Thread reader = new Thread(new Runnable() {
            @Override public void run() { readLoop(); }
        }, "remote-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop() {
        try {
            while (true) {
                Channel.Message m = ch.read();
                if (m.type == Protocol.FRAME) {
                    Bitmap b = BitmapFactory.decodeByteArray(m.data, 0, m.data.length);
                    if (b != null) view.setFrame(b);
                }
                // arquivos e outras mensagens: nao tratados no celular
            }
        } catch (IOException ignored) {
            // sessao encerrada
        } finally {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (!leaving && !isFinishing()) {
                        Toast.makeText(RemoteActivity.this, "Sessão encerrada.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ barra de teclas

    private HorizontalScrollView buildBar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setBackgroundColor(0xFF1F2937);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row);

        add(row, "Teclado", new View.OnClickListener() {
            @Override public void onClick(View v) { showKeyboard(); }
        });
        addKey(row, "Esc", KeyMap.VK_ESC);
        addKey(row, "Tab", KeyMap.VK_TAB);
        ctrlBtn = add(row, "Ctrl", toggle(0));
        altBtn = add(row, "Alt", toggle(1));
        shiftBtn = add(row, "Shift", toggle(2));
        winBtn = add(row, "Win", toggle(3));
        addKey(row, "←", KeyMap.VK_LEFT);
        addKey(row, "↑", KeyMap.VK_UP);
        addKey(row, "↓", KeyMap.VK_DOWN);
        addKey(row, "→", KeyMap.VK_RIGHT);
        addKey(row, "Enter", KeyMap.VK_ENTER);
        addKey(row, "Apagar", KeyMap.VK_BACKSPACE);
        addKey(row, "Del", KeyMap.VK_DELETE);
        addKey(row, "Home", KeyMap.VK_HOME);
        addKey(row, "End", KeyMap.VK_END);
        addKey(row, "PgUp", KeyMap.VK_PGUP);
        addKey(row, "PgDn", KeyMap.VK_PGDN);
        dragBtn = add(row, "Arrastar", new View.OnClickListener() {
            @Override public void onClick(View v) {
                view.dragMode = !view.dragMode;
                refreshToggles();
            }
        });
        add(row, "Ajustar", new View.OnClickListener() {
            @Override public void onClick(View v) { view.resetZoom(); }
        });
        add(row, "Sair", new View.OnClickListener() {
            @Override public void onClick(View v) { confirmExit(); }
        });
        return scroll;
    }

    private Button add(LinearLayout row, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0xFF374151);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(6, 6, 0, 6);
        row.addView(b, lp);
        return b;
    }

    private void addKey(LinearLayout row, String text, final int vk) {
        add(row, text, new View.OnClickListener() {
            @Override public void onClick(View v) { view.tapKey(vk); }
        });
    }

    private View.OnClickListener toggle(final int which) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) {
                switch (which) {
                    case 0: view.ctrl = !view.ctrl; break;
                    case 1: view.alt = !view.alt; break;
                    case 2: view.shift = !view.shift; break;
                    default: view.win = !view.win; break;
                }
                refreshToggles();
            }
        };
    }

    private void refreshToggles() {
        paint(ctrlBtn, view.ctrl);
        paint(altBtn, view.alt);
        paint(shiftBtn, view.shift);
        paint(winBtn, view.win);
        paint(dragBtn, view.dragMode);
    }

    private static void paint(Button b, boolean on) {
        b.setBackgroundColor(on ? 0xFF2563EB : 0xFF374151);
    }

    private void showKeyboard() {
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, 0);
    }

    // ------------------------------------------------------------------ saida

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setMessage("Encerrar a sessão com " + Session.hostName + "?")
                .setNegativeButton("Continuar", null)
                .setPositiveButton("Encerrar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        leaving = true;
                        finish();
                    }
                })
                .show();
    }

    @Override public void onBackPressed() {
        confirmExit();
    }

    @Override protected void onDestroy() {
        leaving = true;
        if (view != null) view.shutdown();
        Session.close();
        super.onDestroy();
    }
}
