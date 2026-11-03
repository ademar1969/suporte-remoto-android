package com.remoto.mobile;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;

/** Canal de mensagens tipadas sobre um socket (leitura e escrita thread-safe para envio). */
public final class Channel implements Closeable {

    public static final class Message {
        public final int type;
        public final byte[] data;

        Message(int type, byte[] data) {
            this.type = type;
            this.data = data;
        }
    }

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    public Channel(Socket socket, DataInputStream in, DataOutputStream out) {
        this.socket = socket;
        this.in = in;
        this.out = out;
    }

    public void send(int type, byte[] payload) throws IOException {
        send(type, payload, 0, payload.length);
    }

    public void send(int type, byte[] buf, int off, int len) throws IOException {
        synchronized (out) {
            out.writeByte(type);
            out.writeInt(len);
            out.write(buf, off, len);
            out.flush();
        }
    }

    public Message read() throws IOException {
        int type = in.readUnsignedByte();
        int len = in.readInt();
        if (len < 0 || len > Protocol.MAX_MESSAGE) throw new IOException("Mensagem invalida");
        byte[] b = new byte[len];
        in.readFully(b);
        return new Message(type, b);
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    public static byte[] intBytes(int v) {
        return ByteBuffer.allocate(4).putInt(v).array();
    }

    public static byte[] floatBytes(float a, float b) {
        return ByteBuffer.allocate(8).putFloat(a).putFloat(b).array();
    }
}
