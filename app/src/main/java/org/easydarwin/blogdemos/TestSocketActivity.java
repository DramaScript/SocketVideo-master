package org.easydarwin.blogdemos;

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

import com.vilyever.socketclient.server.SocketServer;

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


/**
 * @CreadBy ：DramaScript
 * @date 2017/8/25
 */
public class TestSocketActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnClickListener {

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
                    Toast.makeText(TestSocketActivity.this, "开启直播失败", Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    Toast.makeText(TestSocketActivity.this, "连接服务器失败", Toast.LENGTH_SHORT).show();
                    break;
                case 3:
                    if (!started) {
                        startPreview();
                    } else {
                        stopPreview();
                    }
                    break;
                case 4:
                    Toast.makeText(TestSocketActivity.this, "socket关闭了连接", Toast.LENGTH_SHORT).show();
                    break;
                case 5:
                    Toast.makeText(TestSocketActivity.this, "socket断开了连接", Toast.LENGTH_SHORT).show();
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
                            // 步骤1：从Socket 获得输出流对象OutputStream
                            // 该对象作用：发送数据
                            outputStream = socket.getOutputStream();
                            // 步骤2：写入需要发送的数据到输出流对象中
                            outputStream.write(outData);
                            // 特别注意：数据的结尾加上换行符才可让服务器端的readline()停止阻塞
                            // 步骤3：发送数据到服务端
                            outputStream.flush();
                            byte[] temp = new byte[4];
                            System.arraycopy(outData, 0, temp, 0, 4);
                            Log.e("writeSteam", "正在写入数据长度：" + outData.length + ",前四个字节的值：" + bytesToInt(temp, 0));
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

    private List<Byte> byteList = new ArrayList<>();
    private List<Integer> index = new ArrayList<>();
    private int flag = 0;

    private void startSocketListener() {
        byte[] head = {0x00, 0x00, 0x00, 0x01};
        // 利用线程池直接开启一个线程 & 执行该线程
        // 步骤1：创建输入流对象InputStream
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
                                    Log.e("readSteam", "接收的数据长度："  +out.length);
                                    if (le != -1) {
                                        for (Byte b:byteList){
                                            Log.e("after","上次剩余数据："+b.byteValue());
                                        }
                                        for (byte data : out) {
                                            byteList.add(data);
                                            Log.e("tag","正在录入数据："+data);
                                        }
                                        for (Byte b:byteList){
                                            Log.e("tagfter","录入之后的新数据："+b.byteValue()+", 长度："+byteList.size());
                                        }
                                        Log.e("tag","out："+out.length+", byteList："+byteList.size());
                                        for (int i = 0; i < byteList.size(); i++) {
                                            if (i + 3 <= out.length) {
                                                if (byteList.get(i).byteValue() == 0x00 && byteList.get(i + 1).byteValue() == 0x00 && byteList.get(i + 2).byteValue() == 0x00 && byteList.get(i + 3).byteValue() == 0x01) {
                                                    index.add(i);
                                                }
                                            }
                                        }
                                        Log.e("index","index="+index.size());
                                        if (index.size()>=2){

                                            //截取其中的一帧
                                            byte[] frameBy = new byte[index.get(1)-index.get(0)];
                                            int a = 0;
                                            for (int i = index.get(0); i <=index.get(1)-1; i++) {
                                                frameBy[a] = byteList.get(i).byteValue();
                                                a++;
                                            }
                                            //传给H264解码器
                                            for (byte b:frameBy){
                                                Log.e("indecode","传入解码的数据："+b);
                                            }
                                            if (frameBy.length!=0){
                                                mPlayer.decodeH264(frameBy);
                                            }
                                            Log.e("tag","frameBy的长度："+frameBy.length);
                                            //从集合中删除上一帧之前的数据
                                            for (int i = index.get(1)-1; i >=0; i--) {
                                                byteList.remove(i);
                                            }
                                            for (Byte b:byteList){
                                                Log.e("after","删除之后的剩余数据："+b.byteValue());
                                            }
                                            index.clear();
                                        }else {

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

    private synchronized void getFreame(int le,byte[] out){

    }

    public byte[] subBytes(byte[] src, int begin, int count) {
        byte[] bs = new byte[count];
        for (int i = begin; i < begin + count; i++) bs[i - begin] = src[i];
        return bs;
    }

    public static int bytesToInt(byte[] src, int offset) {
        int value;
        value = (int) ((src[offset] & 0xFF)
                | ((src[offset + 1] & 0xFF) << 8)
                | ((src[offset + 2] & 0xFF) << 16)
                | ((src[offset + 3] & 0xFF) << 24));
        return value;
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

