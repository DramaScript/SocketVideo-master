package org.easydarwin.blogdemos;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;

import org.easydarwin.blogdemos.audio.AACDecoderUtil;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;


/**
 * @CreadBy ：DramaScript
 * @date 2017/8/25
 */
public class WatchMovieActivity extends AppCompatActivity {

    private Socket socket;
    private VideoSurfaceView surfaceView;
    String path = Environment.getExternalStorageDirectory() + "/socket.h264";

    private AvcDecode mPlayer = null;
    private SurfaceHolder holder;

    private Thread threadListener;
    private byte[] last;
    //音频解码器
    private AACDecoderUtil audioUtil;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch);
        surfaceView = (VideoSurfaceView) findViewById(R.id.surfaceView);
        holder = surfaceView.getHolder();
        Log.e("readSteam", "-----------------1");
//        holder.addCallback(this);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("播放直播");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getTitle().equals("播放直播")) {
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    socket = App.getInstance().getSocket();
                    mPlayer = new AvcDecode(640, 480, holder.getSurface());
                    startSocketListener();
                }
            }.start();
        }
        return super.onOptionsItemSelected(item);
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

                                                    if (i + 40 + frameLength <= addByte.length) {//表明还可以凑齐一帧
                                                        byte[] frameBy = new byte[frameLength];
                                                        System.arraycopy(addByte, i + 40, frameBy, 0, frameBy.length);
                                                        if (type.equals("video")) {
                                                            mPlayer.decodeH264(frameBy);
                                                        } else if (type.equals("music")) {
                                                            if (audioUtil==null){
                                                                audioUtil =  new AACDecoderUtil();
                                                                audioUtil.start();
                                                            }
                                                            audioUtil.decode(frameBy,0,frameLength);
                                                        }

                                                        i = i + 38 + frameLength;
//                                                        Thread.sleep(20);
                                                    } else {
                                                        //变成结余数据
                                                        last = new byte[addByte.length - i];
                                                        System.arraycopy(addByte, i, last, 0, last.length);
                                                        break;
                                                    }
                                                }
                                            } else {//直接是剩余的
                                                last = new byte[addByte.length - i];
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


}