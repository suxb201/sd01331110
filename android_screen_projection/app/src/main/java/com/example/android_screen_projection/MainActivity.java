package com.example.android_screen_projection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.uuzuche.lib_zxing.activity.CaptureActivity;
import com.uuzuche.lib_zxing.activity.CodeUtils;
import com.uuzuche.lib_zxing.activity.ZXingLibrary;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.media.AudioRecord.RECORDSTATE_RECORDING;
import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity {
    private static final int CODE_SCREEN_RECODED = 1;
    private static final int CODE_ZXING = 2;
    private static final String TAG = "screen_projection";

    //    private AtomicBoolean is_stop = new AtomicBoolean(false);
    private AtomicBoolean is_running = new AtomicBoolean(false);
    // -------------- 录屏
    private String video_ip = "192.168.1.102";
    private Integer video_port = 10009;
    private MediaProjectionManager projectionManager;// 获取 projection

    // -------------- 录音
    private String audio_ip = "192.168.1.102";
    private Integer audio_port = 10010;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // 开始录
        Button button_start = findViewById(R.id.button_start);
        button_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (is_running.get()) {
                    Toast.makeText(MainActivity.this, "Recorder is already start!", Toast.LENGTH_SHORT).show();
                    return;
                }

                ZXingLibrary.initDisplayOpinion(MainActivity.this);
                String[] permissions = new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                };
                requestPermissions(permissions, 200);
                Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
                intent.setAction("com.google.zxing.client.android.SCAN");
                try {
                    startActivityForResult(intent, CODE_ZXING);
                } catch (Exception e) {
                    Log.d("e", e.toString());
                }

            }
        });

        // 停止录
        Button button_end = findViewById(R.id.button_stop);
        button_end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StopRecorder(v);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "onActivityResult: " + requestCode);

        if (requestCode == CODE_SCREEN_RECODED) { // 点击开始按钮
            start_video_record(resultCode, data);
            start_audio_record();
        } else if (requestCode == CODE_ZXING) {
            Bundle bundle = data.getExtras();

            if (bundle != null && bundle.getInt(CodeUtils.RESULT_TYPE) == CodeUtils.RESULT_SUCCESS) {
                String result = bundle.getString(CodeUtils.RESULT_STRING);
                System.out.println(result);
                assert result != null;
                String[] info = result.split(",");
                video_ip = info[0];
                audio_ip = info[0];
                video_port = Integer.parseInt(info[1]);
                audio_port = Integer.parseInt(info[2]);
                StartRecorder();
            } else {
                Toast.makeText(this, "扫码失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void StartRecorder() {

        is_running.set(true);
        // 获取 projectionManager
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        // 开始屏幕捕获
        startActivityForResult(projectionManager.createScreenCaptureIntent(), CODE_SCREEN_RECODED);

    }

    public void StopRecorder(View view) {
        is_running.set(false);
        Toast.makeText(this, "停止录屏", Toast.LENGTH_SHORT).show();
    }

    private void start_video_record(final int resultCode, final Intent data) {
        new Thread() {
            @Override
            public void run() {
                try {
                    MediaProjection mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                    final SocketSender video_socket_sender = new SocketSender(video_ip, video_port);

                    int width = 1080;
                    int height = 1920;
                    MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                    format.setInteger(MediaFormat.KEY_BIT_RATE, 60000000);
                    format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
                    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);

                    MediaCodec mediaCodec = MediaCodec.createEncoderByType("video/avc");
                    mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    // 会记录到这个 surface

                    mediaProjection.createVirtualDisplay(
                            TAG, width, height, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                            mediaCodec.createInputSurface(), null, null
                    );

                    mediaCodec.start();
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    while (is_running.get()) {
                        int index = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                        if (index >= 0) {
                            ByteBuffer encodedData = mediaCodec.getOutputBuffer(index);
                            assert encodedData != null;
                            final byte[] bytes = new byte[encodedData.remaining()];
                            encodedData.get(bytes);
                            video_socket_sender.send(bytes);
                            mediaCodec.releaseOutputBuffer(index, false);
                        }
                    }
                    mediaCodec.stop();
                    video_socket_sender.close();
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();

//        try {
//            MediaProjection mediaProjection = projectionManager.getMediaProjection(resultCode, data);
//            final BufferSender video_socket_sender = new BufferSender(video_ip, video_port);
//
//            int width = 1080;
//            int height = 1920;
//            MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
//            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//            format.setInteger(MediaFormat.KEY_BIT_RATE, 60000000);
//            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
//            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
//
//            mediaCodec = MediaCodec.createEncoderByType("video/avc");
//            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//            // 会记录到这个 surface
//            Surface surface = mediaCodec.createInputSurface();
//
//            mediaProjection.createVirtualDisplay(
//                    TAG, width, height, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
//                    surface, null, null
//            );
//            mediaCodec.setCallback(new MediaCodec.Callback() {
//                @Override
//                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
//                }
//
//                @Override
//                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
//                    if (index >= 0) {
//                        ByteBuffer encodedData = codec.getOutputBuffer(index);
//                        assert encodedData != null;
//                        final byte[] bytes = new byte[encodedData.remaining()];
//                        encodedData.get(bytes);
//
//                        video_socket_sender.send(encodedData);
//
//                        mediaCodec.releaseOutputBuffer(index, false);
//                    }
//                }
//
//                @Override
//                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
//                }
//
//                @Override
//                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
//                }
//            });
//            mediaCodec.start();
//        } catch (IOException |
//                InterruptedException e) {
//            e.printStackTrace();
//        }

        Toast.makeText(this, "正在录屏", Toast.LENGTH_SHORT).show();
    }

    private void start_audio_record() {
        final AudioRecord audio_record = createAudioRecord();

        new Thread() {
            @Override
            public void run() {
                try {
                    SocketSender audio_socket_sender = new SocketSender(audio_ip, audio_port);
                    byte[] audio_data = new byte[1000];
                    audio_record.startRecording();
                    while (audio_record.getRecordingState() == RECORDSTATE_RECORDING && is_running.get()) {
                        audio_record.read(audio_data, 0, 1000);
                        audio_socket_sender.send(audio_data);
                    }
                    audio_socket_sender.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private AudioRecord createAudioRecord() {
        for (int sampleRate : new int[]{44100}) {
            for (short audioFormat : new short[]{AudioFormat.ENCODING_PCM_16BIT}) {
                for (short channelConfig : new short[]{AudioFormat.CHANNEL_IN_MONO}) {

                    try {
                        int recBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

                        if (recBufSize < 0) {
                            continue;
                        }

                        AudioRecord audioRecord = new AudioRecord(
                                MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                                audioFormat, recBufSize * 2
                        );

                        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                            return audioRecord;
                        }

                        audioRecord.release();
                        audioRecord = null;
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
            }
        }
        throw new IllegalStateException("getInstance() failed : no suitable audio configurations on this device.");
    }

}