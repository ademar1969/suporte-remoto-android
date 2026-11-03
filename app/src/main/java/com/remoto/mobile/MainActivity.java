package com.remoto.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tela principal: conectar por ID, lista de IDs acessados com bolinha online/offline. */
public class MainActivity extends Activity {

    private static final int GREEN = 0xFF22A559, RED = 0xFFD64545, GRAY = 0xFF9CA3AF, MUTED = 0xFF6B7280,
            ACCENT = 0xFF2563EB;

    private Store store;
    private EditText idField, passField;
    private TextView idDot, message;
    private CheckBox rememberBox;
    private Button connectBtn;
    private RecentsAdapter adapter;
    private List<String> recents = new ArrayList<>();

    private volatile Map<String, Boolean> statusMap = Collections.emptyMap();
    private volatile String typedId = "";
    private String autoFilledId;

    private volatile boolean connecting;
    private volatile boolean cancelled;
    private volatile Socket pending;
    private volatile int pollGen;

    // ================================================================== ciclo de vida / UI

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        setContentView(buildUi());
        if (store.host().isEmpty()) {
            // primeiro uso: mostra o passo a passo e depois pede o servidor
            startActivity(new Intent(this, HelpActivity.class));
            showServerDialog();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshRecents();
        startPolling();
    }

    @Override protected void onPause() {
        pollGen++; // para o polling
        super.onPause();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF3F4F6);
        root.setPadding(dp(16), dp(16), dp(16), dp(8));

        // cabecalho
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(AppInfo.NAME);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF111827);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(smallButton("Como usar", new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, HelpActivity.class)); }
        }));
        head.addView(smallButton("Servidor", new View.OnClickListener() {
            @Override public void onClick(View v) { showServerDialog(); }
        }));
        head.addView(smallButton("Apoie", new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, AboutActivity.class)); }
        }));
        root.addView(head);

        label(root, "ID do computador");
        LinearLayout idRow = new LinearLayout(this);
        idRow.setOrientation(LinearLayout.HORIZONTAL);
        idRow.setGravity(Gravity.CENTER_VERTICAL);
        idDot = new TextView(this);
        idDot.setText("●");
        idDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        idDot.setTextColor(GRAY);
        idDot.setPadding(0, 0, dp(8), 0);
        idRow.addView(idDot);
        idField = new EditText(this);
        idField.setInputType(InputType.TYPE_CLASS_PHONE);
        idField.setHint("000 000 000");
        idField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        idField.setTypeface(null, Typeface.BOLD);
        idField.setSingleLine(true);
        idRow.addView(idField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(idRow);

        label(root, "Senha (opcional)");
        passField = new EditText(this);
        passField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passField.setHint("Em branco: o outro lado precisa aceitar");
        passField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        passField.setSingleLine(true);
        root.addView(passField);

        rememberBox = new CheckBox(this);
        rememberBox.setText("Lembrar a senha deste ID");
        rememberBox.setChecked(true);
        rememberBox.setTextColor(MUTED);
        root.addView(rememberBox);

        connectBtn = new Button(this);
        connectBtn.setText("Conectar");
        connectBtn.setAllCaps(false);
        connectBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        connectBtn.setTextColor(Color.WHITE);
        connectBtn.setBackgroundColor(ACCENT);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (connecting) cancelConnect(); else connect();
            }
        });
        root.addView(connectBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        message = new TextView(this);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        message.setPadding(0, dp(6), 0, dp(6));
        root.addView(message);

        label(root, "RECENTES  (toque para preencher, segure para mais opções)");
        adapter = new RecentsAdapter();
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setBackgroundColor(Color.WHITE);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                idField.setText(formatId(recents.get(pos)));
                if (passField.getText().length() == 0) passField.requestFocus();
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                itemMenu(recents.get(pos));
                return true;
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView legend = new TextView(this);
        legend.setText("● online   ● offline   ● sem resposta do servidor");
        legend.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        legend.setTextColor(MUTED);
        SpannableStringBuilder sb = new SpannableStringBuilder(legend.getText());
        colorDot(sb, 0, GREEN);
        colorDot(sb, sb.toString().indexOf("● offline"), RED);
        colorDot(sb, sb.toString().indexOf("● sem"), GRAY);
        legend.setText(sb);
        legend.setPadding(0, dp(6), 0, 0);
        root.addView(legend);

        idField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { onIdChanged(); }
        });
        return root;
    }

    private static void colorDot(SpannableStringBuilder sb, int at, int color) {
        if (at < 0) return;
        sb.setSpan(new ForegroundColorSpan(color), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(1.6f), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private Button smallButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        return b;
    }

    private void label(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(MUTED);
        t.setPadding(0, dp(12), 0, 0);
        root.addView(t);
    }

    private void showMessage(String text, boolean error) {
        message.setText(text);
        message.setTextColor(error ? RED : MUTED);
    }

    // ================================================================== ID digitado / senha salva

    private static String digits(CharSequence s) { return s.toString().replaceAll("\\D", ""); }

    static String formatId(String id) {
        if (id.length() != 9) return id;
        return id.substring(0, 3) + " " + id.substring(3, 6) + " " + id.substring(6);
    }

    private void onIdChanged() {
        String d = digits(idField.getText());
        typedId = d;
        if (autoFilledId != null && !d.equals(autoFilledId)) {
            passField.setText("");
            autoFilledId = null;
        }
        if (d.length() == 9 && autoFilledId == null && passField.getText().length() == 0) {
            String saved = store.password(d);
            if (saved != null) {
                passField.setText(saved);
                autoFilledId = d;
            }
        }
        updateIdDot();
    }

    private void updateIdDot() {
        String d = typedId;
        Boolean on = d.length() == 9 ? statusMap.get(d) : null;
        idDot.setTextColor(dotColor(on));
    }

    private static int dotColor(Boolean online) { return online == null ? GRAY : online ? GREEN : RED; }

    // ================================================================== lista de recentes

    private void refreshRecents() {
        recents = store.recents();
        adapter.notifyDataSetChanged();
    }

    private final class RecentsAdapter extends BaseAdapter {
        @Override public int getCount() { return recents.size(); }
        @Override public Object getItem(int i) { return recents.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            TextView tv = convert instanceof TextView ? (TextView) convert : new TextView(MainActivity.this);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            tv.setTextColor(0xFF111827);
            tv.setPadding(dp(12), dp(12), dp(12), dp(12));

            String id = recents.get(pos);
            String name = store.displayName(id);
            SpannableStringBuilder sb = new SpannableStringBuilder();
            colorDotAppend(sb, dotColor(statusMap.get(id)));
            sb.append("  ");
            if (name != null) {
                int s = sb.length();
                sb.append(name);
                sb.setSpan(new StyleSpan(Typeface.BOLD), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append("  ·  ");
            }
            sb.append(formatId(id));
            if (store.hasPassword(id)) {
                int s = sb.length();
                sb.append("   senha salva");
                sb.setSpan(new RelativeSizeSpan(0.65f), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(MUTED), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            tv.setText(sb);
            return tv;
        }
    }

    private static void colorDotAppend(SpannableStringBuilder sb, int color) {
        int s = sb.length();
        sb.append("●");
        colorDot(sb, s, color);
    }

    private void itemMenu(final String id) {
        final String[] items = {"Conectar", "Renomear (apelido)...", "Esquecer só a senha salva", "Esquecer este ID"};
        new AlertDialog.Builder(this)
                .setTitle(formatId(id))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            idField.setText(formatId(id));
                            connect();
                        } else if (which == 1) {
                            rename(id);
                        } else if (which == 2) {
                            store.forgetPassword(id);
                            if (id.equals(autoFilledId)) {
                                passField.setText("");
                                autoFilledId = null;
                            }
                            refreshRecents();
                        } else {
                            confirmForget(id);
                        }
                    }
                })
                .show();
    }

    private void rename(final String id) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(store.nickname(id) == null ? "" : store.nickname(id));
        input.setHint(store.knownName(id) == null ? "Apelido" : store.knownName(id));
        new AlertDialog.Builder(this)
                .setTitle("Apelido para " + formatId(id))
                .setMessage("Em branco: usa o nome do computador.")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        store.setNickname(id, input.getText().toString());
                        refreshRecents();
                    }
                })
                .show();
    }

    private void confirmForget(final String id) {
        new AlertDialog.Builder(this)
                .setMessage("Esquecer o ID " + formatId(id) + " e a senha salva dele?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Esquecer", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        store.removeRecent(id);
                        if (id.equals(autoFilledId)) {
                            passField.setText("");
                            autoFilledId = null;
                        }
                        refreshRecents();
                    }
                })
                .show();
    }

    // ================================================================== servidor

    private void showServerDialog() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), 0);

        final EditText host = new EditText(this);
        host.setHint("Servidor (IP ou DNS)");
        host.setSingleLine(true);
        host.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        host.setText(store.host());
        col.addView(host);

        final EditText port = new EditText(this);
        port.setHint("Porta");
        port.setSingleLine(true);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText(String.valueOf(store.port()));
        col.addView(port);

        final EditText name = new EditText(this);
        name.setHint("Nome deste aparelho (aparece para o outro lado)");
        name.setSingleLine(true);
        name.setText(store.deviceName());
        col.addView(name);

        new AlertDialog.Builder(this)
                .setTitle("Servidor")
                .setView(col)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        int p;
                        try {
                            p = Integer.parseInt(port.getText().toString().trim());
                        } catch (NumberFormatException e) {
                            p = Protocol.DEFAULT_PORT;
                        }
                        if (p < 1 || p > 65535) p = Protocol.DEFAULT_PORT;
                        store.setServer(host.getText().toString(), p);
                        store.setDeviceName(name.getText().toString());
                        startPolling();
                    }
                })
                .show();
    }

    // ================================================================== status online/offline

    private void startPolling() {
        final int gen = ++pollGen;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (gen == pollGen) {
                    Set<String> ids = new LinkedHashSet<>(store.recents());
                    String typed = typedId;
                    if (typed.length() == 9) ids.add(typed);
                    Map<String, Boolean> r = Net.queryStatus(store.host(), store.port(), new ArrayList<>(ids));
                    if (gen != pollGen) return;
                    statusMap = r == null ? Collections.<String, Boolean>emptyMap() : r;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            adapter.notifyDataSetChanged();
                            updateIdDot();
                        }
                    });
                    try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
                }
            }
        }, "status");
        t.setDaemon(true);
        t.start();
    }

    // ================================================================== conexao

    private void connect() {
        if (connecting) return;
        final String id = digits(idField.getText());
        if (id.length() != 9) {
            showMessage("Digite o ID de 9 dígitos.", true);
            return;
        }
        if (store.host().isEmpty()) {
            showServerDialog();
            return;
        }
        final String pw = passField.getText().toString();
        final boolean remember = rememberBox.isChecked();
        connecting = true;
        cancelled = false;
        connectBtn.setText("Cancelar");
        showMessage("Conectando... se não houver senha, o outro lado precisa aceitar.", false);
        Thread t = new Thread(new Runnable() {
            @Override public void run() { outgoing(id, pw, remember); }
        }, "connect");
        t.setDaemon(true);
        t.start();
    }

    private void cancelConnect() {
        cancelled = true;
        Socket s = pending;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    private void outgoing(String target, String pw, boolean remember) {
        Socket s = new Socket();
        pending = s;
        String fail = null;
        boolean rejectedSaved = false;
        boolean started = false;
        try {
            s.connect(new InetSocketAddress(store.host(), store.port()), 8000);
            s.setTcpNoDelay(true);
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            out.writeUTF(Protocol.ROLE_CONNECT);
            out.writeInt(AppInfo.PROTOCOL);
            out.writeUTF(target);
            out.flush();

            s.setSoTimeout(30_000);
            String r = in.readUTF();
            if (!"OK".equals(r)) {
                fail = r.startsWith("ERRO: ") ? r.substring(6) : r;
            } else {
                s.setSoTimeout(150_000); // o outro lado pode precisar aceitar
                out.writeUTF("AUTH");
                out.writeUTF(store.deviceName());
                out.writeUTF(pw);
                out.flush();
                String res = in.readUTF();
                fail = Net.authMessage(res);
                if ("FAIL".equals(res) && store.hasPassword(target)) {
                    store.forgetPassword(target); // senha salva ficou velha: evita travar o ID por tentativas
                    rejectedSaved = true;
                    fail += " A senha salva foi removida.";
                }
                if (fail == null) {
                    String hostName = Store.clean(in.readUTF());
                    s.setSoTimeout(0);
                    store.addRecent(target);
                    store.setKnownName(target, hostName);
                    if (remember && !pw.isEmpty()) store.setPassword(target, pw);
                    Session.start(new Channel(s, in, out), hostName, target);
                    started = true;
                }
            }
        } catch (ConnectException | UnknownHostException e) {
            fail = "Não foi possível conectar ao servidor " + store.host() + ":" + store.port()
                    + ". Confira o endereço e se o servidor está no ar.";
        } catch (SocketTimeoutException e) {
            fail = "Tempo esgotado. Verifique o servidor e tente novamente.";
        } catch (IOException e) {
            fail = cancelled ? "Conexão cancelada." : "Falha na conexão: " + e.getMessage();
        } finally {
            if (!started) {
                try { s.close(); } catch (IOException ignored) { }
            }
            pending = null;
        }

        final String fm = fail;
        final boolean cleared = rejectedSaved;
        final boolean go = started;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                connecting = false;
                connectBtn.setText("Conectar");
                if (cleared) {
                    passField.setText("");
                    autoFilledId = null;
                }
                refreshRecents();
                if (go) {
                    showMessage("", false);
                    startActivity(new Intent(MainActivity.this, RemoteActivity.class));
                } else if (fm != null) {
                    showMessage(fm, true);
                }
            }
        });
    }
}
