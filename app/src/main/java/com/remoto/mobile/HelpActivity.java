package com.remoto.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Passo a passo de uso (aberto no primeiro uso e pelo botao "Como usar"). */
public class HelpActivity extends Activity {

    private static final int TEXT = 0xFF111827, MUTED = 0xFF4B5563, ACCENT = 0xFF2563EB, WARN = 0xFF92400E;

    private static final String[][] STEPS = {
            {"Deixe o servidor no ar",
                    "Num computador com IP ou DNS público, rode o servidor (porta 5000 por padrão) "
                            + "e libere essa porta no firewall."},
            {"Abra o cliente no computador a controlar",
                    "Instale e abra o cliente desktop. Na tela principal ele mostra o \"Seu ID\". "
                            + "Anote esse número. Se quiser entrar sem ninguém aceitar, defina uma senha "
                            + "mestre em Configurações."},
            {"Configure o servidor no celular",
                    "Toque em Servidor, informe o IP ou DNS e a porta do seu servidor, dê um nome ao "
                            + "aparelho e salve. É o mesmo servidor usado pelo cliente desktop."},
            {"Conecte",
                    "Digite o ID do computador (ou toque num da lista de recentes) e a senha. Com a senha "
                            + "em branco, alguém no computador precisa aceitar a conexão. Depois toque em Conectar."},
            {"Controle pela tela",
                    "Um dedo move o cursor. Toque faz o clique esquerdo e toque longo o clique direito. "
                            + "Dois dedos para cima ou para baixo rolam a página. Abra ou feche dois dedos "
                            + "(pinça) para dar zoom; com zoom, arraste com dois dedos para mover a imagem. "
                            + "O botão Ajustar volta para a tela inteira."},
            {"Use os botões de apoio",
                    "Arrastar faz o dedo segurar o botão esquerdo (selecionar texto, mover janelas). "
                            + "Teclado abre o teclado do celular; Ctrl, Alt, Shift e Win valem para a próxima tecla."},
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final float d = getResources().getDisplayMetrics().density;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) (20 * d), (int) (24 * d), (int) (20 * d), (int) (24 * d));

        TextView title = new TextView(this);
        title.setText("Como usar");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(TEXT);
        col.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Em poucos passos você controla seu computador pelo celular.");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sub.setTextColor(MUTED);
        sub.setPadding(0, (int) (6 * d), 0, (int) (12 * d));
        col.addView(sub);

        for (int i = 0; i < STEPS.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, (int) (14 * d), 0, (int) (10 * d));

            TextView num = new TextView(this);
            num.setText(String.valueOf(i + 1));
            num.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            num.setTypeface(null, Typeface.BOLD);
            num.setTextColor(Color.WHITE);
            num.setGravity(Gravity.CENTER);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(ACCENT);
            num.setBackground(dot);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams((int) (30 * d), (int) (30 * d));
            nlp.rightMargin = (int) (12 * d);
            row.addView(num, nlp);

            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);

            TextView head = new TextView(this);
            head.setText(STEPS[i][0]);
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            head.setTypeface(null, Typeface.BOLD);
            head.setTextColor(TEXT);
            body.addView(head);

            TextView txt = new TextView(this);
            txt.setText(STEPS[i][1]);
            txt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            txt.setTextColor(MUTED);
            txt.setLineSpacing(0, 1.1f);
            body.addView(txt);

            row.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            col.addView(row);
        }

        TextView dots = new TextView(this);
        dots.setText("Na lista de recentes: bolinha verde = computador online, vermelha = offline, "
                + "cinza = servidor sem resposta. Segure um ID para dar um apelido.");
        dots.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        dots.setTextColor(MUTED);
        dots.setPadding(0, (int) (12 * d), 0, 0);
        col.addView(dots);

        TextView warn = new TextView(this);
        warn.setText("Importante: a conexão ainda não é criptografada. Use uma VPN se for acessar "
                + "por redes que você não controla.");
        warn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        warn.setTextColor(WARN);
        warn.setPadding(0, (int) (14 * d), 0, (int) (14 * d));
        col.addView(warn);

        Button ok = new Button(this);
        ok.setText("Entendi");
        ok.setAllCaps(false);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        col.addView(ok, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }
}
