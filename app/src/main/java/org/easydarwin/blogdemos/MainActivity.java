package org.easydarwin.blogdemos;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import org.easydarwin.blogdemos.audio.AacEncode;
import org.easydarwin.blogdemos.hw.EncoderDebugger;
import org.easydarwin.blogdemos.hw.NV21Convertor;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static android.R.attr.data;


/**
 * @CreadBy ：DramaScript
 * @date 2017/8/25
 */
public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnClickListener {

    String path = Environment.getExternalStorageDirectory() + "/vv831.h264";

    int width = 640, height = 480;
    int framerate, bitrate;
    int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    MediaCodec mMediaCodec;
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    Camera mCamera;
    NV21Convertor mConvertor;
    Button btnSwitch;
    boolean started = false;
    private Socket socket;

    private SurfaceView video_play;
    private AvcDecode mPlayer = null;


    // 输出流对象
    OutputStream outputStream;

    // 记录是否正在进行录制
    private boolean isRecording = false;
    //录制音频参数
    private int frequence = 44100; //录制频率，单位hz.这里的值注意了，写的不好，可能实例化AudioRecord对象的时候，会出错。我开始写成11025就不行。这取决于硬件设备
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

    private byte[] last;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1:
                    Toast.makeText(MainActivity.this, "开启直播失败", Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    Toast.makeText(MainActivity.this, "连接服务器失败", Toast.LENGTH_SHORT).show();
                    break;
                case 3:
                    if (!started) {
                        startPreview();
                        startRecord();
                    } else {
                        stopPreview();
                    }
                    break;
                case 4:
                    Toast.makeText(MainActivity.this, "socket关闭了连接", Toast.LENGTH_SHORT).show();
                    break;
                case 5:
                    Toast.makeText(MainActivity.this, "socket断开了连接", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
    private Thread threadListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnSwitch = (Button) findViewById(R.id.btn_switch);
        btnSwitch.setOnClickListener(this);
        initMediaCodec();
        surfaceView = (SurfaceView) findViewById(R.id.sv_surfaceview);
        video_play = (SurfaceView) findViewById(R.id.video_play);
        surfaceView.getHolder().addCallback(this);
        surfaceView.getHolder().setFixedSize(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            //申请WRITE_EXTERNAL_STORAGE权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        doNext(requestCode, grantResults);
    }


    private void doNext(int requestCode, int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted

            } else {
                // Permission Denied
                //  displayFrameworkBugMessageAndExit();
                Toast.makeText(this, "请在应用管理中打开“相机”访问权限！", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }


    private void initMediaCodec() {
        int dgree = getDgree();
        framerate = 15;
        bitrate = 2 * width * height * framerate / 20;
        EncoderDebugger debugger = EncoderDebugger.debug(getApplicationContext(), width, height);
        mConvertor = debugger.getNV21Convertor();
        try {
            mMediaCodec = MediaCodec.createByCodecName(debugger.getEncoderName());
            MediaFormat mediaFormat;
            if (dgree == 0) {
                mediaFormat = MediaFormat.createVideoFormat("video/avc", height, width);
            } else {
                mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
            }
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    debugger.getEncoderColorFormat());
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int[] determineMaximumSupportedFramerate(Camera.Parameters parameters) {
        int[] maxFps = new int[]{0, 0};
        List<int[]> supportedFpsRanges = parameters.getSupportedPreviewFpsRange();
        for (Iterator<int[]> it = supportedFpsRanges.iterator(); it.hasNext(); ) {
            int[] interval = it.next();
            if (interval[1] > maxFps[1] || (interval[0] > maxFps[0] && interval[1] == maxFps[1])) {
                maxFps = interval;
            }
        }
        return maxFps;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("看直播");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getTitle().equals("看直播")) {
            Intent intent = new Intent(this, WatchMovieActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean ctreateCamera(SurfaceHolder surfaceHolder) {
        try {
            mCamera = Camera.open(mCameraId);
            Camera.Parameters parameters = mCamera.getParameters();
            int[] max = determineMaximumSupportedFramerate(parameters);
            Camera.CameraInfo camInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(mCameraId, camInfo);
            int cameraRotationOffset = camInfo.orientation;
            int rotate = (360 + cameraRotationOffset - getDgree()) % 360;
            parameters.setRotation(rotate);
            parameters.setPreviewFormat(ImageFormat.NV21);
            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
            parameters.setPreviewSize(width, height);
            parameters.setPreviewFpsRange(max[0], max[1]);
            mCamera.setParameters(parameters);
//            mCamera.autoFocus(null);
            int displayRotation;
            displayRotation = (cameraRotationOffset - getDgree() + 360) % 360;
            mCamera.setDisplayOrientation(displayRotation);
            mCamera.setPreviewDisplay(surfaceHolder);

            return true;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stack = sw.toString();
            Toast.makeText(this, stack, Toast.LENGTH_LONG).show();
            destroyCamera();
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceHolder = holder;
        ctreateCamera(surfaceHolder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
        destroyCamera();
    }

    Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        byte[] mPpsSps = new byte[0];

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (data == null) {
                return;
            }
            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
            byte[] dst = new byte[data.length];
            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
            if (getDgree() == 0) {
                dst = Util.rotateNV21Degree90(data, previewSize.width, previewSize.height);
            } else {
                dst = data;
            }
            try {
                int bufferIndex = mMediaCodec.dequeueInputBuffer(5000000);
                if (bufferIndex >= 0) {
                    inputBuffers[bufferIndex].clear();
                    mConvertor.convert(dst, inputBuffers[bufferIndex]);
                    mMediaCodec.queueInputBuffer(bufferIndex, 0,
                            inputBuffers[bufferIndex].position(),
                            System.nanoTime() / 1000, 0);
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    while (outputBufferIndex >= 0) {
                        ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                        byte[] outData = new byte[bufferInfo.size];
                        outputBuffer.get(outData);
                        //记录pps和sps
                        if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1 && outData[4] == 103) {
                            mPpsSps = outData;
                        } else if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1 && outData[4] == 101) {
                            //在关键帧前面加上pps和sps数据
                            byte[] iframeData = new byte[mPpsSps.length + outData.length];
                            System.arraycopy(mPpsSps, 0, iframeData, 0, mPpsSps.length);
                            System.arraycopy(outData, 0, iframeData, mPpsSps.length, outData.length);
                            outData = iframeData;
                        }
                        //  将数据用socket传输
                        writeData(outData,1);
//                        mPlayer.decodeH264(outData);
                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    }
                } else {
                    Log.e("easypusher", "No buffer available !");
                }
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                String stack = sw.toString();
                Log.e("save_log", stack);
                e.printStackTrace();
            } finally {
                mCamera.addCallbackBuffer(dst);
            }
        }

    };

    private void codeCYuv(byte[] data){

    }

    /**
     * 开始录音
     */
    private void startRecord(){
        isRecording = true;
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    //根据定义好的几个配置，来获取合适的缓冲大小
                    int bufferSize = AudioRecord.getMinBufferSize(frequence, channelConfig, audioEncoding);
                    //实例化AudioRecord
                    AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, frequence, channelConfig, audioEncoding, bufferSize);
                    //开始录制
                    record.startRecording();
                    AacEncode aacMediaEncode = new AacEncode();
                    //定义缓冲
                    byte[] buffer = new byte[bufferSize];
                    //定义循环，根据isRecording的值来判断是否继续录制
                    while (isRecording) {
                        //从bufferSize中读取字节。
                        int bufferReadResult = record.read(buffer, 0, bufferSize);
                        //获取字节流
                        if (AudioRecord.ERROR_INVALID_OPERATION != bufferReadResult) {
                            //转成AAC编码
                            byte[] ret = aacMediaEncode.offerEncoder(buffer);
                            Log.e("recod","aac大小："+ret.length);
                            writeData(ret,2);
                        }
                    }
                    //录制结束
                    record.stop();
                    //释放编码器
                    aacMediaEncode.close();
                    // dos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * 将数据传输给服务器
     *
     * @param outData
     */
    private void writeData(final byte[] outData, final int type) {
        new Thread() {
            @Override
            public void run() {
                try {
                    if (!socket.isClosed()) {
                        if (socket.isConnected()) {
                            outputStream = socket.getOutputStream();
                            //给每一帧加一个自定义的头
                            if (outData.length!=0){
                                byte[] headOut = creatHead(outData,type);
                                outputStream.write(headOut);
                                outputStream.flush();
                                Log.e("writeSteam", "加入头部后写入数据长度：" + headOut.length);
                            }
                        } else {
                            Log.e("writeSteam", "发送失败，socket断开了连接");
                        }
                    } else {
                        Log.e("writeSteam", "发送失败，socket关闭");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("writeSteam", "写入数据失败");
                }
            }
        }.start();
    }

    /**
     * 给每一帧添加一个头
     */
    private byte[] creatHead(byte[] out,int type) {
        String head = "";
        if (type==1){
            head = "start&video&" + System.currentTimeMillis() + "&" + out.length + "&end";
        }else {
            head = "start&music&" + System.currentTimeMillis() + "&" + out.length + "&end";
        }
        byte[] headBytes = new byte[40];
        System.arraycopy(head.getBytes(), 0, headBytes, 0, head.getBytes().length);
        Log.e("writeSteam", "头部长度：" + headBytes.length);
        for (byte b:"start".getBytes()){
            Log.e("writeSteam", "头部数据："+b);
        }
        if (headBytes[0] == 0x73 && headBytes[1] == 0x74 && headBytes[2] == 0x61 && headBytes[3] == 0x72 && headBytes[4] == 0x74) {
            Log.e("writeSteam", "确认是头部");
        }
        String outHead = new String(headBytes);
        Log.e("writeSteam", "头部：" + outHead);
        String[] headSplit = outHead.split("&");
        for (String s : headSplit) {
            Log.e("writeSteam", "截取部分：" + s);
        }
        Log.e("writeSteam", "加入头部前数据长度：" + out.length);
        byte[] headByteOut = new byte[out.length + 40];
        //将头部拷入数组
        System.arraycopy(headBytes, 0, headByteOut, 0, headBytes.length);
        //将帧数据拷入数组
        System.arraycopy(out, 0, headByteOut, headBytes.length, out.length);
        return headByteOut;
    }

    private void startSocketListener() {
        threadListener = new Thread() {
            @Override
            public void run() {
                super.run();
                while (true) {
                    if (!socket.isClosed()) {
                        if (socket.isConnected()) {
                            try {
                                // 步骤1：创建输入流对象InputStream
                                InputStream is = socket.getInputStream();
                                if (is != null) {
                                    DataInputStream input = new DataInputStream(is);
                                    byte[] bytes = new byte[10000];
                                    int le = input.read(bytes);
                                    byte[] out = new byte[le];
                                    System.arraycopy(bytes, 0, out, 0, out.length);
                                    Util.save(out, 0, out.length, path, true);
                                    Log.e("readSteam", "接收的数据长度：" + out.length);
                                    if (le != -1) {
                                        byte[] addByte = new byte[out.length];
                                        if (last != null) {
                                            if (last.length != 0) {
                                                for (byte b : last) {
//                                                    Log.e("last", "-剩余数据##########################" + b);
                                                }
                                                //将上次结余的数据拼接在新来数据前面
                                                addByte = new byte[out.length + last.length];
                                                System.arraycopy(last, 0, addByte, 0, last.length);
                                                System.arraycopy(out, 0, addByte, last.length, out.length);
                                                for (byte b : addByte) {
//                                                    Log.e("addByte", "-合并的数据++++++++++++++++++++++" + b);
                                                }
                                            }
                                        } else {
                                            addByte = new byte[out.length];
                                            System.arraycopy(out, 0, addByte, 0, out.length);
                                            for (byte b : addByte) {
//                                                Log.e("addByte", "合并的数据++++++++++++++++++++++" + b);
                                            }
                                        }

                                        for (int i = 0; i < addByte.length; i++) {
//                                            Log.e("readSteam", "接收的数据" + addByte[i]);
                                            if (i + 39 < addByte.length) {
                                                //先截取返回字符串的前40位，判断是否是头
                                                byte[] head = new byte[40];
//                                                Log.e("readSteam", "所在位置：" + i);
                                                System.arraycopy(addByte, i, head, 0, head.length);
                                                //判读是否是帧头
                                                if (head[0] == 0x73 && head[1] == 0x74 && head[2] == 0x61 && head[3] == 0x72 && head[4] == 0x74) {

                                                    String hd = new String(head);
                                                    String[] headSplit = hd.split("&");
                                                    for (String s : headSplit) {
//                                                        Log.e("readSteam", "截取部分：" + s);
                                                    }
                                                    String type = headSplit[1];
                                                    String time = headSplit[2];
                                                    String len = headSplit[3];
                                                    int frameLength = Integer.parseInt(len);
//                                                    index.add(i+40);
//                                                    Log.e("readSteam", "==================================================================：" + frameLength+",    "+addByte.length);

                                                    if (i+40+frameLength<=addByte.length){//表明还可以凑齐一帧
                                                        byte[] frameBy = new byte[frameLength];
                                                        System.arraycopy(addByte, i+40, frameBy, 0, frameBy.length);
                                                        if (type.equals("video")){
                                                            mPlayer.decodeH264(frameBy);
                                                        }else if (type.equals("music")){

                                                        }

                                                        i = i+38+frameLength;
//                                                        Thread.sleep(20);
                                                    }else {
                                                        //变成结余数据
                                                        last = new byte[addByte.length-i];
                                                        System.arraycopy(addByte, i, last, 0, last.length);
                                                        break;
                                                    }
                                                }
                                            }else {//直接是剩余的
                                                last = new byte[addByte.length-i];
                                                System.arraycopy(addByte, i, last, 0, last.length);
                                                break;
                                            }
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
//                            Log.e("readSteam", "接受失败，socket断开了连接");
                        }
                    } else {
//                        Log.e("readSteam", "接受失败，socket关闭");
                    }
                }
            }
        };
        threadListener.start();
    }

    /**
     * 开启预览
     */
    public synchronized void startPreview() {
        if (mCamera != null && !started) {
            mCamera.startPreview();
            int previewFormat = mCamera.getParameters().getPreviewFormat();
            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
            int size = previewSize.width * previewSize.height
                    * ImageFormat.getBitsPerPixel(previewFormat)
                    / 8;
            mCamera.addCallbackBuffer(new byte[size]);
            mCamera.setPreviewCallbackWithBuffer(previewCallback);
            started = true;
            btnSwitch.setText("停止");
            mPlayer = new AvcDecode(width, height, video_play.getHolder().getSurface());
            startSocketListener();
        }
    }

    /**
     * 停止预览
     */
    public synchronized void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
            started = false;
            btnSwitch.setText("开始");
            try {
                if (socket != null) {
                    if (socket.isConnected()) {
                        socket.close();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
//            threadListener.stop();
        }
    }

    /**
     * 销毁Camera
     */
    protected synchronized void destroyCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            try {
                mCamera.release();
            } catch (Exception e) {

            }
            mCamera = null;
        }
    }

    private int getDgree() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break; // Natural orientation
            case Surface.ROTATION_90:
                degrees = 90;
                break; // Landscape left
            case Surface.ROTATION_180:
                degrees = 180;
                break;// Upside down
            case Surface.ROTATION_270:
                degrees = 270;
                break;// Landscape right
        }
        return degrees;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_switch:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        socket = App.getInstance().getSocket();
                        Message msg = Message.obtain();
                        if (socket == null) {
                            msg.what = 1;
                            handler.sendMessage(msg);
                        } else if (!socket.isConnected()) {
                            msg.what = 2;
                            handler.sendMessage(msg);
                        } else {
                            msg.what = 3;
                            handler.sendMessage(msg);
                        }
                    }
                }).start();

                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyCamera();
        mMediaCodec.stop();
        mMediaCodec.release();
        mMediaCodec = null;
    }
}

