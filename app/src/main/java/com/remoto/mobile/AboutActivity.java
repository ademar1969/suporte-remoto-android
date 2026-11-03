package com.remoto.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Sobre o app e contribuicao via Pix (QR e "copia e cola"). */
public class AboutActivity extends Activity {

    /** Pix "copia e cola" (o mesmo do QR em res/drawable-nodpi/qr_pix.png). */
    static final String PIX_COPIA_E_COLA =
            "00020101021126580014br.gov.bcb.pix013699675610-739a-4d77-be48-d8d2f402"
                    + "0d775204000053039865802BR5914SUPORTE REMOTO6006BRASIL62070503***63045B86";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final float d = getResources().getDisplayMetrics().density;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding((int) (20 * d), (int) (24 * d), (int) (20 * d), (int) (24 * d));

        TextView title = new TextView(this);
        title.setText(AppInfo.NAME + " " + AppInfo.VERSION);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(0xFF111827);
        col.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Controle seus computadores pelo celular.");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sub.setTextColor(0xFF6B7280);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, (int) (6 * d), 0, (int) (20 * d));
        col.addView(sub);

        TextView ask = new TextView(this);
        ask.setText("Faça parte desta comunidade");
        ask.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        ask.setTypeface(null, android.graphics.Typeface.BOLD);
        ask.setTextColor(0xFF111827);
        ask.setGravity(Gravity.CENTER);
        col.addView(ask);

        TextView story = new TextView(this);
        story.setText("O Suporte Remoto é gratuito e cresce com o carinho de quem o usa. "
                + "Se ele já te ajudou a resolver um problema ou a socorrer alguém à distância, "
                + "que tal retribuir? Qualquer valor, de um cafezinho em diante, ajuda a manter o "
                + "servidor no ar, corrigir erros e criar novidades que todo mundo aproveita.\n\n"
                + "Contribuir é totalmente opcional, mas cada Pix é um empurrão para o programa "
                + "ficar melhor para toda a comunidade. Muito obrigado por estar com a gente!");
        story.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        story.setTextColor(0xFF374151);
        story.setGravity(Gravity.CENTER);
        story.setLineSpacing(0, 1.15f);
        story.setPadding(0, (int) (10 * d), 0, 0);
        col.addView(story);

        ImageView qr = new ImageView(this);
        qr.setImageResource(R.drawable.qr_pix);
        qr.setBackgroundColor(Color.WHITE);
        qr.setAdjustViewBounds(true);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams((int) (260 * d), (int) (260 * d));
        qlp.topMargin = (int) (12 * d);
        qlp.bottomMargin = (int) (12 * d);
        col.addView(qr, qlp);

        Button copy = new Button(this);
        copy.setText("Copiar Pix copia e cola");
        copy.setAllCaps(false);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Pix", PIX_COPIA_E_COLA));
                    Toast.makeText(AboutActivity.this, "Código Pix copiado.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        col.addView(copy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(this);
        note.setText("Abra o app do seu banco, escolha Pix > Ler QR Code (ou Pix copia e cola).");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setTextColor(0xFF6B7280);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, (int) (12 * d), 0, 0);
        col.addView(note);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }
}
