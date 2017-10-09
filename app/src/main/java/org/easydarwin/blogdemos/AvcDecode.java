package org.easydarwin.blogdemos;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.R.attr.data;

/**
 * @CreadBy ：DramaScript
 * @date 2017/8/29
 */
public class AvcDecode {

    //解码类型
    String MIME_TYPE = "video/avc";

    MediaCodec mediaCodec = null;//这里是建立的解码器

    ByteBuffer[] inputBuffers = null;
    int m_framerate = 24;//帧率

    //pts时间基数
    long presentationTimeUs = 0;

    public AvcDecode(int mWidth, int mHeigh, Surface surface) {

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(
                MIME_TYPE, mWidth, mHeigh);
        try {
            mediaCodec = MediaCodec.createDecoderByType(MIME_TYPE);
            mediaCodec.configure(mediaFormat, surface, null, 0);//注意上面编码器的注释，看看区别
            mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        inputBuffers = mediaCodec.getInputBuffers();
    }


    public boolean decodeH264(byte[] h264) {
        // Get input buffer index
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(100);//-1表示等待

        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(h264);

            //计算pts
            long pts = computePresentationTime(presentationTimeUs);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, h264.length, pts, 0);
            presentationTimeUs += 1;

        } else {
            return false;
        }

        // Get output buffer index
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 100);
        while (outputBufferIndex >= 0) {
            mediaCodec.releaseOutputBuffer(outputBufferIndex, true);//到这里为止应该有图像显示了
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
        Log.e("Media", "onFrame end");
        return true;

    }

    /**
     * 计算pts
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / m_framerate;
    }

}
