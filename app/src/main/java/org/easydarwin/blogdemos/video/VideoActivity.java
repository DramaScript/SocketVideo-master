package org.easydarwin.blogdemos.video;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
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

import org.easydarwin.blogdemos.App;
import org.easydarwin.blogdemos.AvcDecode;
import org.easydarwin.blogdemos.R;
import org.easydarwin.blogdemos.Util;
import org.easydarwin.blogdemos.WatchMovieActivity;
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;


/**
 * @CreadBy ：DramaScript
 * @date 2017/8/25
 */
public class VideoActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnClickListener {

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

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1:
                    Toast.makeText(VideoActivity.this, "开启直播失败", Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    Toast.makeText(VideoActivity.this, "连接服务器失败", Toast.LENGTH_SHORT).show();
                    break;
                case 3:
                    if (!started) {
                        startPreview();
                    } else {
                        stopPreview();
                    }
                    break;
                case 4:
                    Toast.makeText(VideoActivity.this, "socket关闭了连接", Toast.LENGTH_SHORT).show();
                    break;
                case 5:
                    Toast.makeText(VideoActivity.this, "socket断开了连接", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
    private Thread threadListener;
    private byte[] last;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnSwitch = (Button) findViewById(R.id.btn_switch);
        btnSwitch.setOnClickListener(this);
        initMediaCodec();
        surfaceView = (SurfaceView) findViewById(R.id.sv_surfaceview);
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
                        writeData(outData);
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

    // 输出流对象
    OutputStream outputStream;

    /**
     * 将数据传输给服务器
     *
     * @param outData
     */
    private void writeData(final byte[] outData) {
        new Thread() {
            @Override
            public void run() {
                try {
                    if (!socket.isClosed()) {
                        if (socket.isConnected()) {
                            outputStream = socket.getOutputStream();
                            //给每一帧加一个自定义的头

                            outputStream.write(outData);
                            outputStream.flush();
                            byte[] temp = new byte[4];
                            System.arraycopy(outData, 0, temp, 0, 4);
                            Log.e("writeSteam", "正在写入数据长度：" + outData.length );
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

    //保存每次返回来的数据
    private List<byte[]> frameList = new ArrayList<>();
    //根据实际情况调整一帧的大小
    private int FRAME_MAX_LEN = 500*1024;
    //这个值用于找到第一个帧头后，继续寻找第二个帧头，如果解码失败可以尝试缩小这个值
    private int FRAME_MIN_LEN = 6;


    private void startSocketListener() {
        byte[] head = {0x00, 0x00, 0x00, 0x01};
        // 利用线程池直接开启一个线程 & 执行该线程
        // 步骤1：创建输入流对象InputStream
        threadListener = new Thread() {
            @Override
            public void run() {
                super.run();
                //开始时间
                long startTime = System.currentTimeMillis();
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
                                    Log.e("readSteam", "接收的数据长度：------------------------"  +out.length);
                                    if (le != -1) {
                                        byte[] addByte = new byte[out.length];
                                        if (last!=null){
                                            if (last.length!=0){
                                                for (byte b: last){
                                                    Log.e("last", "-剩余数据##########################"  +b);
                                                }
                                                //将上次结余的数据拼接在新来数据前面
                                                addByte = new byte[out.length+last.length];
                                                System.arraycopy(last, 0, addByte, 0,  last.length);
                                                System.arraycopy(out, 0, addByte, last.length,  out.length);
                                                for (byte b: addByte){
                                                    Log.e("addByte", "-合并的数据++++++++++++++++++++++"  +b);
                                                }
                                            }
                                        }else {
                                            addByte = new byte[out.length];
                                            System.arraycopy(out, 0, addByte, 0,  out.length);
                                            for (byte b: addByte){
                                                Log.e("addByte", "合并的数据++++++++++++++++++++++"  +b);
                                            }
                                        }

                                        List<Integer> index = new ArrayList<>();
                                        for (int i=0;i<addByte.length;i++){
                                            Log.e("readSteam", "接收的数据"  +addByte[i]);
                                            if (i+3<=addByte.length){
                                                if (isHead(addByte,i)){
                                                    index.add(i);
                                                }
                                            }
                                        }

                                        Log.e("readsize", "头文的数量=============================="  +index.size());
                                        if (index.size()>=2){
                                            for (int a=0;a<index.size();a++){
                                                if (a+1<index.size()){
                                                    byte[] frameBy = new byte[index.get(a+1)-index.get(a)];
                                                    System.arraycopy(addByte, index.get(a), frameBy, 0,  frameBy.length);
                                                    Log.e("readcodec", "一帧数据长度：******************"  +frameBy.length);
                                                    mPlayer.decodeH264(frameBy);
//                                                    frameList.add(frameBy);
                                                }else {
                                                    //变成结余数据
                                                    last = new byte[addByte.length-index.get(index.size()-1)];
                                                    System.arraycopy(addByte, index.get(index.size()-1), last, 0,  addByte.length-index.get(index.size()-1));
                                                }
                                            }
                                        }else {
                                            //直接变成结余的
                                            last = new byte[addByte.length];
                                            System.arraycopy(addByte, 0, last, 0,  addByte.length);
                                        }
                                    }
                                    //线程休眠
                                    sleepThread(startTime, System.currentTimeMillis());
                                    //重置开始时间
                                    startTime = System.currentTimeMillis();
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

    private void playH264(){
        new Thread(){
            @Override
            public void run() {
                super.run();
                while (true){
                    Log.e("start","-------------缓冲区大小："+frameList.size());
                    if (frameList.size()>5){
                        for (int i=0;i<frameList.size();i++){
                            Log.e("start","-------------开始播放");
                            mPlayer.decodeH264(frameList.get(i));
                            frameList.remove(i);
                        }
                    }
                }
            }
        }.start();
    }


    private void startPlay(){
        new Thread(){
            @Override
            public void run() {
                super.run();
                //循环从队列中读取数据
                while (true){
                    if (frameList.size()!=0){
                        //保存完整数据帧
                        byte[] frame = new byte[FRAME_MAX_LEN];
                        //当前帧长度
                        int frameLen = 0;
                        //开始时间
                        long startTime = System.currentTimeMillis();
                        //每次从消息队列读取的数据,返回第一个元素，并且在队列中删除
                        byte[] readData = frameList.get(0);
                        //当前长度小于最大值
                        if (frameLen + readData.length< FRAME_MAX_LEN){
                            //将readData拷贝到frame
                            System.arraycopy(readData, 0, frame, frameLen,  readData.length);
                            //修改frameLen
                            frameLen += readData.length;
                            //寻找第一个帧头
                            int headFirstIndex = findHead(frame, 0, frameLen);
                            while (headFirstIndex >= 0 && isHead(frame, headFirstIndex)) {
                                //寻找第二个帧头
                                int headSecondIndex = findHead(frame, headFirstIndex + FRAME_MIN_LEN, frameLen);
                                //如果第二个帧头存在，则两个帧头之间的就是一帧完整的数据
                                if (headSecondIndex > 0 && isHead(frame, headSecondIndex)) {
                                    mPlayer.decodeH264(frame);
                                    for (byte b:frame){
                                        Log.e("codec", "正在解码数据：" + b );
                                    }
                                    //截取headSecondIndex之后到frame的有效数据,并放到frame最前面
                                    byte[] temp = Arrays.copyOfRange(frame, headSecondIndex, frameLen);
                                    System.arraycopy(temp, 0, frame, 0, temp.length);
                                    //修改frameLen的值
                                    frameLen = temp.length;
                                    //线程休眠
                                    sleepThread(startTime, System.currentTimeMillis());
                                    //重置开始时间
                                    startTime = System.currentTimeMillis();
                                    //继续寻找数据帧
                                    headFirstIndex = findHead(frame, 0, frameLen);
                                }
                            }
                        }
                    }
                }
            }
        }.start();
    }

    /**
     * 寻找指定buffer中h264头的开始位置
     *
     * @param data   数据
     * @param offset 偏移量
     * @param max    需要检测的最大值
     * @return h264头的开始位置 ,-1表示未发现
     */
    private int findHead(byte[] data, int offset, int max) {
        int i;
        for (i = offset; i <= max; i++) {
            //发现帧头
            if (isHead(data, i))
                break;
        }
        //检测到最大值，未发现帧头
        if (i == max) {
            i = -1;
        }
        return i;
    }

    /**
     * 判断是否是I帧/P帧头:
     * 00 00 00 01 65    (I帧)
     * 00 00 00 01 61 / 41   (P帧)
     *
     * @param data
     * @param offset
     * @return 是否是帧头
     */
    private boolean isHead(byte[] data, int offset) {
        boolean result = false;
        // 00 00 00 01
        if (data[offset] == 0x00 && data[offset + 1] == 0x00
                && data[offset + 2] == 0x00 && data[3] == 0x01 ) {
            result = true;
        }
       /* // 00 00 01
        if (data[offset] == 0x00 && data[offset + 1] == 0x00
                && data[offset + 2] == 0x01 && isVideoFrameHeadType(data[offset + 3])) {
            result = true;
        }*/
        return result;
    }

    /**
     * I帧或者P帧
     */
    private boolean isVideoFrameHeadType(byte head) {
        return head == (byte) 0x65 || head == (byte) 0x61 || head == (byte) 0x41;
    }


    //根据帧率获取的解码每帧需要休眠的时间,根据实际帧率进行操作
    private int PRE_FRAME_TIME = 1000 / 25;

    //修眠
    private void sleepThread(long startTime, long endTime) {
        //根据读文件和解码耗时，计算需要休眠的时间
        long time = PRE_FRAME_TIME - (endTime - startTime);
        if (time > 0) {
            try {
                Thread.sleep(time);
                Log.e("tag","休眠了："+time);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
//            playH264();
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

