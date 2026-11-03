package com.remoto.mobile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Net {
    private Net() {}

    /** @return ID -> online?, ou null se o servidor nao respondeu (ou e uma versao sem esta consulta). */
    static Map<String, Boolean> queryStatus(String host, int port, List<String> ids) {
        if (host.isEmpty() || ids.isEmpty()) return null;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 4000);
            s.setSoTimeout(4000);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            out.writeUTF(Protocol.ROLE_STATUS);
            out.writeInt(AppInfo.PROTOCOL);
            out.writeInt(ids.size());
            for (String id : ids) out.writeUTF(id);
            out.flush();
            String r = in.readUTF();
            if (r.length() != ids.size() || !r.matches("[01]*")) return null;
            Map<String, Boolean> m = new HashMap<>();
            for (int i = 0; i < ids.size(); i++) m.put(ids.get(i), r.charAt(i) == '1');
            return m;
        } catch (IOException e) {
            return null;
        }
    }

    static String authMessage(String res) {
        switch (res) {
            case "OK": return null;
            case "FAIL": return "Senha incorreta.";
            case "DENIED": return "O usuário do outro computador recusou a conexão.";
            case "BUSY": return "O computador remoto já está em outra sessão.";
            case "LOCKED": return "Muitas tentativas incorretas. Tente novamente em 1 minuto.";
            default: return "Resposta inesperada: " + res;
        }
    }
}
