package com.example.android_screen_projection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Thread.sleep;

public class SocketSender {
    Socket socket = new Socket();
    OutputStream output_stream = null;

    SocketSender(final String ip, final Integer port) throws IOException, InterruptedException {
        connect(ip, port);
    }


    void send(byte[] bytes) throws IOException {
        output_stream.write(bytes);
        output_stream.flush();
    }

    void close() throws IOException {
        socket.close();
    }

    private void connect(String ip, Integer port) throws InterruptedException {
        AtomicBoolean connected = new AtomicBoolean(false);
        while (!connected.get()) {
            try {
                socket.connect(new InetSocketAddress(ip, port), 1000);
                output_stream = socket.getOutputStream();
                System.out.println("socket 连接成功" + port);
                connected.set(true);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("socket 连接失败 重连");
                sleep(1000);
            }
        }
    }

}