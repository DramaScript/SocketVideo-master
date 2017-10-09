package org.easydarwin.blogdemos;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * @CreadBy ：DramaScript
 * @date 2017/8/29
 */
public class AvcEncoder {

    private static final String TAG = "AvcEncoder";

    private MediaCodec mediaCodec;
    int m_width;//宽
    int m_height;//高
    int m_framerate;//帧率

    byte[] m_info = null;
    //转成后的数据
    private byte[] yuv420 = null;
    //旋转后的数据
    private byte[] rotateYuv420 = null;

    //编码类型
    private String mime = "video/avc";

    //pts时间基数
    long presentationTimeUs = 0;


    /**
     * 构造方法
     *
     * @param width
     * @param height
     * @param framerate 帧数
     * @param bitrate   码流  2500000
     */
    @SuppressWarnings("deprecation")
    @SuppressLint("NewApi")
    public AvcEncoder(int width, int height, int framerate, int bitrate) {
        m_width = width;
        m_height = height;
        m_framerate = framerate;
        //这里的大小要通过计算，而不是网上简单的 *3/2
        yuv420 = new byte[getYuvBuffer(width, height)];
        rotateYuv420 = new byte[getYuvBuffer(width, height)];

        //确定当前MediaCodec支持的图像格式
        int colorFormat = selectColorFormat(selectCodec(mime), mime);

        try {
            mediaCodec = MediaCodec.createEncoderByType(mime);
            //正常的编码出来是横屏的。因为手机本身采集的数据默认就是横屏的
            // MediaFormat mediaFormat = MediaFormat.createVideoFormat(mime, width, height);
            //如果你需要旋转90度或者270度，那么需要把宽和高对调。否则会花屏。因为比如你320 X 240，图像旋转90°之后宽高变成了240 X 320。
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(mime, height, width);
            //设置参数
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);  //COLOR_FormatYUV420SemiPlanar  COLOR_FormatYUV420Planar
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5); //关键帧间隔时间 单位s
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @SuppressLint("NewApi")
    public void close() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 开始编码
     *
     * @author：gj
     * @date: 2017/5/27
     * @time: 14:55
     **/
    @SuppressLint("NewApi")
    public int offerEncoder(byte[] input, byte[] output) {
        int pos = 0;
        //这里根据你设置的采集格式调用。我这里是nv21
        //swapYV12toI420(input, yuv420, m_width, m_height);
        NV21ToNV12(input, rotateYuv420, m_width, m_height);
        //把视频逆时针旋转90度。（正常视觉效果）
        YUV420spRotate90Anticlockwise(rotateYuv420, yuv420, m_width, m_height);
        try {
            @SuppressWarnings("deprecation")
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            @SuppressWarnings("deprecation")
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(yuv420);

                //计算pts，这个值是一定要设置的
                long pts = computePresentationTime(presentationTimeUs);

                mediaCodec.queueInputBuffer(inputBufferIndex, 0, yuv420.length, pts, 0);
                presentationTimeUs += 1;
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if (m_info != null) {
                    System.arraycopy(outData, 0, output, pos, outData.length);
                    pos += outData.length;

                } else {
                    //保存pps sps 只有开始时 第一个帧里有， 保存起来后面用
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
                    if (spsPpsBuffer.getInt() == 0x00000001) {
                        m_info = new byte[outData.length];
                        System.arraycopy(outData, 0, m_info, 0, outData.length);
                    } else {
                        return -1;
                    }
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }

            if (output[4] == 0x65) {
                //key frame   编码器生成关键帧时只有 00 00 00 01 65 没有pps sps， 要加上
                System.arraycopy(output, 0, yuv420, 0, pos);
                System.arraycopy(m_info, 0, output, 0, m_info.length);
                System.arraycopy(yuv420, 0, output, m_info.length, pos);
                pos += m_info.length;
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

        return pos;
    }


    //-----------下面是常用的格式转换方法-----------------------------

    //yv12 转 yuv420p  yvu -> yuv,yuv420p就是I420格式，使用极其广泛
    private void swapYV12toI420(byte[] yv12bytes, byte[] i420bytes, int width, int height) {
        System.arraycopy(yv12bytes, 0, i420bytes, 0, width * height);
        System.arraycopy(yv12bytes, width * height + width * height / 4, i420bytes, width * height, width * height / 4);
        System.arraycopy(yv12bytes, width * height, i420bytes, width * height + width * height / 4, width * height / 4);
    }

    //选择了YUV420SP作为编码的目标颜色空间，其实YUV420SP就是NV12，咱们CAMERA设置的是NV21，所以需要转一下
    private void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return;
        int framesize = width * height;
        int i = 0, j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for (i = 0; i < framesize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j] = nv21[j + framesize - 1];
        }
    }

    private void swapYV12toNV12(byte[] yv12bytes, byte[] nv12bytes, int width, int height) {

        int nLenY = width * height;
        int nLenU = nLenY / 4;
        System.arraycopy(yv12bytes, 0, nv12bytes, 0, width * height);
//        for (int i = 0; i < nLenU; i++) {
//            nv12bytes[nLenY + 2 * i] = yv12bytes[nLenY + i];
//            nv12bytes[nLenY + 2 * i + 1] = yv12bytes[nLenY + nLenU + i];
//        }
        for (int i = 0; i < nLenU; i++) {
            nv12bytes[nLenY + 2 * i + 1] = yv12bytes[nLenY + i];
            nv12bytes[nLenY + 2 * i] = yv12bytes[nLenY + nLenU + i];
        }
    }

    private void swapNV12toI420(byte[] nv12bytes, byte[] i420bytes, int width, int height) {

        int nLenY = width * height;
        int nLenU = nLenY / 4;
        System.arraycopy(nv12bytes, 0, i420bytes, 0, width * height);
        for (int i = 0; i < nLenU; i++) {
            i420bytes[nLenY + i] = nv12bytes[nLenY + 2 * i + 1];
            i420bytes[nLenY + nLenU + i] = nv12bytes[nLenY + 2 * i];
        }
    }

    public Bitmap rawByteArray2RGBABitmap2(byte[] data, int width, int height) {
        int frameSize = width * height;
        int[] rgba = new int[frameSize];

        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++) {
                int y = (0xff & ((int) data[i * width + j]));
                int u = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 0]));
                int v = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 1]));
                y = y < 16 ? 16 : y;

                int r = Math.round(1.164f * (y - 16) + 1.596f * (v - 128));
                int g = Math.round(1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                int b = Math.round(1.164f * (y - 16) + 2.018f * (u - 128));

                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);

                rgba[i * width + j] = 0xff000000 + (b << 16) + (g << 8) + r;
            }

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.setPixels(rgba, 0, width, 0, 0, width, height);
        return bmp;
    }


    //计算YUV的buffer的函数，需要根据文档计算，而不是简单“*3/2”
    public int getYuvBuffer(int width, int height) {
        // stride = ALIGN(width, 16)
        int stride = (int) Math.ceil(width / 16.0) * 16;
        // y_size = stride * height
        int y_size = stride * height;
        // c_stride = ALIGN(stride/2, 16)
        int c_stride = (int) Math.ceil(width / 32.0) * 16;
        // c_size = c_stride * height/2
        int c_size = c_stride * height / 2;
        // size = y_size + c_size * 2
        return y_size + c_size * 2;
    }


    //通过mimeType确定支持的格式
    private int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
    }

    private boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    private MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    /**
     * 计算视频pts
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / m_framerate;
    }

    //顺时针旋转270度
    private void YUV420spRotate270(byte[] des, byte[] src, int width, int height) {
        int n = 0;
        int uvHeight = height >> 1;
        int wh = width * height;
        //copy y
        for (int j = width - 1; j >= 0; j--) {
            for (int i = 0; i < height; i++) {
                des[n++] = src[width * i + j];
            }
        }

        for (int j = width - 1; j > 0; j -= 2) {
            for (int i = 0; i < uvHeight; i++) {
                des[n++] = src[wh + width * i + j - 1];
                des[n++] = src[wh + width * i + j];
            }
        }
    }

    //旋转180度（顺时逆时结果是一样的）
    private void YUV420spRotate180(byte[] src, byte[] des, int width, int height) {

        int n = 0;
        int uh = height >> 1;
        int wh = width * height;
        //copy y
        for (int j = height - 1; j >= 0; j--) {
            for (int i = width - 1; i >= 0; i--) {
                des[n++] = src[width * j + i];
            }
        }


        for (int j = uh - 1; j >= 0; j--) {
            for (int i = width - 1; i > 0; i -= 2) {
                des[n] = src[wh + width * j + i - 1];
                des[n + 1] = src[wh + width * j + i];
                n += 2;
            }
        }
    }

    //顺时针旋转90
    private void YUV420spRotate90Clockwise(byte[] src, byte[] dst, int srcWidth, int srcHeight) {
//        int wh = width * height;
//        int k = 0;
//        for (int i = 0; i < width; i++) {
//            for (int j = height - 1; j >= 0; j--) {
//                des[k] = src[width * j + i];
//                k++;
//            }
//        }
//        for (int i = 0; i < width; i += 2) {
//            for (int j = height / 2 - 1; j >= 0; j--) {
//                des[k] = src[wh + width * j + i];
//                des[k + 1] = src[wh + width * j + i + 1];
//                k += 2;
//            }
//        }

        int wh = srcWidth * srcHeight;
        int uvHeight = srcHeight >> 1;

        //旋转Y
        int k = 0;
        for (int i = 0; i < srcWidth; i++) {
            int nPos = 0;
            for (int j = 0; j < srcHeight; j++) {
                dst[k] = src[nPos + i];
                k++;
                nPos += srcWidth;
            }
        }

        for (int i = 0; i < srcWidth; i += 2) {
            int nPos = wh;
            for (int j = 0; j < uvHeight; j++) {
                dst[k] = src[nPos + i];
                dst[k + 1] = src[nPos + i + 1];
                k += 2;
                nPos += srcWidth;
            }
        }

    }

    //逆时针旋转90
    private void YUV420spRotate90Anticlockwise(byte[] src, byte[] dst, int width, int height) {
        int wh = width * height;
        int uvHeight = height >> 1;

        //旋转Y
        int k = 0;
        for (int i = 0; i < width; i++) {
            int nPos = width - 1;
            for (int j = 0; j < height; j++) {
                dst[k] = src[nPos - i];
                k++;
                nPos += width;
            }
        }

        for (int i = 0; i < width; i += 2) {
            int nPos = wh + width - 1;
            for (int j = 0; j < uvHeight; j++) {
                dst[k] = src[nPos - i - 1];
                dst[k + 1] = src[nPos - i];
                k += 2;
                nPos += width;
            }
        }

        //不进行镜像翻转
//        for (int i = 0; i < width; i++) {
//            int nPos = width - 1;
//            for (int j = 0; j < height; j++) {
//                dst[k] = src[nPos - i];
//                k++;
//                nPos += width;
//            }
//        }
//        for (int i = 0; i < width; i += 2) {
//            int nPos = wh + width - 2;
//            for (int j = 0; j < uvHeight; j++) {
//                dst[k] = src[nPos - i];
//                dst[k + 1] = src[nPos - i + 1];
//                k += 2;
//                nPos += width;
//            }
//        }

    }

    //镜像
    private void Mirror(byte[] yuv_temp, int w, int h) {
        int i, j;

        int a, b;
        byte temp;
        //mirror y
        for (i = 0; i < h; i++) {
            a = i * w;
            b = (i + 1) * w - 1;
            while (a < b) {
                temp = yuv_temp[a];
                yuv_temp[a] = yuv_temp[b];
                yuv_temp[b] = temp;
                a++;
                b--;
            }
        }
        //mirror u
        int uindex = w * h;
        for (i = 0; i < h / 2; i++) {
            a = i * w / 2;
            b = (i + 1) * w / 2 - 1;
            while (a < b) {
                temp = yuv_temp[a + uindex];
                yuv_temp[a + uindex] = yuv_temp[b + uindex];
                yuv_temp[b + uindex] = temp;
                a++;
                b--;
            }
        }
        //mirror v
        uindex = w * h / 4 * 5;
        for (i = 0; i < h / 2; i++) {
            a = i * w / 2;
            b = (i + 1) * w / 2 - 1;
            while (a < b) {
                temp = yuv_temp[a + uindex];
                yuv_temp[a + uindex] = yuv_temp[b + uindex];
                yuv_temp[b + uindex] = temp;
                a++;
                b--;
            }
        }
    }

}
