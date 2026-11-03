package com.remoto.mobile;

/** Sessao remota em andamento (a conexao e aberta na tela principal e usada na tela remota). */
final class Session {
    private Session() {}

    static volatile Channel channel;
    static volatile String hostName = "";
    static volatile String targetId = "";

    static void start(Channel ch, String name, String id) {
        close();
        channel = ch;
        hostName = name;
        targetId = id;
    }

    static void close() {
        Channel c = channel;
        channel = null;
        if (c != null) c.close();
    }
}
