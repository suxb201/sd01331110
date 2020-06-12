package com.example.android_screen_projection;

import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import android.widget.Toast;

import static java.lang.Thread.sleep;

public class BufferSender {
    Socket socket = new Socket();
    OutputStream output_stream = null;
    ArrayBlockingQueue<ByteBuffer> queue = new ArrayBlockingQueue<>(100); // 队列大小

    BufferSender(final String ip, final Integer port) throws IOException, InterruptedException {


        new Thread() {
            @Override
            public void run() {
                try {
                    connect(ip, port);
                    while (true) {

//                        String data = "";
//
//                        // 原始数据
//                        data += queue.take();
//                        System.out.println("socket 连接成功: " + data);
//                        // 发送时间
//                        String time_stamp = "\\timestamp" + System.currentTimeMillis() + "\\end";
//                        data += time_stamp;
//
//                        // to byte
//                        byte[] data_byte = data.getBytes();

                        // send
//                        System.out.println(queue.size());
                        ByteBuffer buffer = queue.take();
                        final byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes, 0, bytes.length);
                        output_stream.write(bytes);
                        output_stream.flush();

                    } // while
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            } // runs
        }.start();
    }

    public static ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();//copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
    }

    void send(ByteBuffer buffer) {
        queue.offer(clone(buffer));
    }

    private void connect(String ip, Integer port) throws InterruptedException {
        AtomicBoolean connected = new AtomicBoolean(false);
        while (!connected.get()) {
            try {
                socket.connect(new InetSocketAddress(ip, port), 1000);
                output_stream = socket.getOutputStream();
                System.out.println("socket 连接成功");
                connected.set(true);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("socket 连接失败 重连");
                sleep(1000);
            }
        }
    }

}
