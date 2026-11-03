package com.remoto.mobile;

public final class Protocol {
    private Protocol() {}

    public static final int DEFAULT_PORT = 5000;

    // Papeis no handshake com o servidor
    public static final String ROLE_REGISTER = "REGISTER"; // estacao fica online (conexao de controle)
    public static final String ROLE_CONNECT = "CONNECT";   // tecnico pedindo acesso a um ID
    public static final String ROLE_ACCEPT = "ACCEPT";     // estacao aceitando uma sessao
    public static final String ROLE_STATUS = "STATUS";     // consulta rapida: quais IDs estao online
    public static final String ERR_ID_IN_USE = "ERRO: ID em uso por outro computador";

    // Mensagens da sessao: [byte tipo][int tamanho][payload]
    public static final int MOUSE_MOVE = 1;    // float x, float y (0..1)
    public static final int MOUSE_PRESS = 2;   // int botao
    public static final int MOUSE_RELEASE = 3; // int botao
    public static final int MOUSE_WHEEL = 4;   // int rotacao
    public static final int KEY_PRESS = 5;     // int keyCode
    public static final int KEY_RELEASE = 6;   // int keyCode
    public static final int FRAME = 10;        // JPEG da tela (host -> tecnico)
    public static final int TEXT = 30;         // bytes UTF-8: texto digitado no celular (host cola pela area de transferencia)

    public static final int FILE_START = 20;   // UTF nome, long tamanho
    public static final int FILE_CHUNK = 21;   // bytes
    public static final int FILE_END = 22;

    public static final int MAX_STATUS_IDS = 50;           // IDs por consulta de status

    public static final int MAX_MESSAGE = 32 * 1024 * 1024;
    public static final int FILE_CHUNK_SIZE = 32 * 1024;
}
