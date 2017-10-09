package org.easydarwin.blogdemos.audio;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @CreadBy ：DramaScript
 * @date 2017/8/22
 */
public class AacEncode {

    private MediaCodec mediaCodec;
    private String mediaType = "OMX.google.aac.encoder";

    ByteBuffer[] inputBuffers = null;
    ByteBuffer[] outputBuffers = null;
    MediaCodec.BufferInfo bufferInfo;

    //pts时间基数
    long presentationTimeUs = 0;

    //创建一个输入流用来输出转换的数据
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    public AacEncode() {

        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            //mediaCodec = MediaCodec.createByCodecName(mediaType);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 设置音频采样率，44100是目前的标准，但是某些设备仍然支持22050，16000，11025
        final int kSampleRates[] = {8000, 11025, 22050, 44100, 48000};
        //比特率 声音中的比特率是指将模拟声音信号转换成数字声音信号后，单位时间内的二进制数据量，是间接衡量音频质量的一个指标
        final int kBitRates[] = {64000, 96000, 128000};

        //初始化   此格式使用的音频编码技术、音频采样率、使用此格式的音频信道数（单声道为 1，立体声为 2）
        MediaFormat mediaFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, kSampleRates[3], 1);

        mediaFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        //比特率 声音中的比特率是指将模拟声音信号转换成数字声音信号后，单位时间内的二进制数据量，是间接衡量音频质量的一个指标
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, kBitRates[1]);

        //传入的数据大小
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024);// It will
        //设置相关参数
        mediaCodec.configure(mediaFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        //开始
        mediaCodec.start();

        inputBuffers = mediaCodec.getInputBuffers();
        outputBuffers = mediaCodec.getOutputBuffers();
        bufferInfo = new MediaCodec.BufferInfo();
    }


    /**
     * 关闭释放资源
     *
     * @author：gj
     * @date: 2017/4/25
     * @time: 16:19
     **/
    public void close() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 开始编码
     *
     * @author：gj
     * @date: 2017/4/25
     * @time: 16:19
     **/
    public byte[] offerEncoder(byte[] input) throws Exception {
        Log.e("offerEncoder", input.length + " is coming");

        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);//其中需要注意的有dequeueInputBuffer（-1），参数表示需要得到的毫秒数，-1表示一直等，0表示不需要等，传0的话程序不会等待，但是有可能会丢帧。
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(input);
            inputBuffer.limit(input.length);

            //计算pts
            long pts = computePresentationTime(presentationTimeUs);

            mediaCodec
                    .queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
            presentationTimeUs += 1;
        }


        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

        while (outputBufferIndex >= 0) {
            int outBitsSize = bufferInfo.size;
            int outPacketSize = outBitsSize + 7; // 7 is ADTS size
            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + outBitsSize);

            //添加ADTS头
            byte[] outData = new byte[outPacketSize];
            addADTStoPacket(outData, outPacketSize);

            outputBuffer.get(outData, 7, outBitsSize);
            outputBuffer.position(bufferInfo.offset);

            //写到输出流里
            outputStream.write(outData);

            // Log.e("AudioEncoder", outData.length + " bytes written");

            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        }

        //输出流的数据转成byte[]
        byte[] out = outputStream.toByteArray();

        //写完以后重置输出流，否则数据会重复
        outputStream.flush();
        outputStream.reset();

        //返回
        return out;
    }

    /**
     * 给编码出的aac裸流添加adts头字段
     *
     * @param packet    要空出前7个字节，否则会搞乱数据
     * @param packetLen
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 1;  //CPE
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }


    //计算PTS，实际上这个pts对应音频来说作用并不大，设置成0也是没有问题的
    private long computePresentationTime(long frameIndex) {
        return frameIndex * 90000 * 1024 / 44100;
    }

}
