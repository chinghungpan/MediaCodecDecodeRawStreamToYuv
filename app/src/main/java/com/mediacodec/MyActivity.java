package com.mediacodec;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.media.MediaCodec;

import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;

import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;


/**
 * app_name：MediaDecode_raw_H264_H265
 * start class：MyActivity (put image to surface) or MainActivity (save file to yuv ,jpg ,or Bitmap)
 * creator：chinghung_pan
 * project start：2018-7-3 下午3:23:32
 * reference: https://blog.csdn.net/jyt0551/article/details/74502627, https://github.com/xinpengliu/MediaCodecDecodeRawH264
 */

public class MyActivity extends Activity implements SurfaceHolder.Callback {

    private String[] filename = {"/sdcard/Download/h264_sample/h264_1920_1080.h264", "/sdcard/Download/h264_sample/h265_1920_1080.h265"};

    private final String filePath = filename[1];
    private int MediaCodecWidth = 1920;
    private int MediaCodecHeight = 1080;

    //h264 or h265 decode
    private h265Decoder mPlayer = null;
    //    private h264Decoder mPlayer = null;
    Handler handler = null;

    public static ArrayList<Frame> frames = null;
    public static int frameID = 0;

    File encodedFile = new File(filePath);
    InputStream is;

    private byte[] header_sps;
    private byte[] header_pps;
    byte[] readInData;


    private class Frame {
        public int id;
        public byte[] frameData;

        public Frame(int id) {
            this.id = id;
        }
    }

    private String strDataLengthList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Log.e("testCodec", "path=" + encodedFile.getPath() + ", data length=" + encodedFile.length());
            is = new FileInputStream(encodedFile);
            readInData = new byte[(int) encodedFile.length()];


            frameID = 0;
            frames = new ArrayList<Frame>();

            try {
                if ((is.read(readInData, 0, (int) encodedFile.length())) != -1) {

                    SurfaceView sv = new SurfaceView(this);
                    handler = new Handler();
                    sv.getHolder().addCallback(this);
                    setContentView(sv);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        try {
            BufferedReader br = null;

            br = new BufferedReader(new FileReader("/sdcard/Download/h264_sample/mediaCodecEncode.txt"));

            StringBuilder sb = new StringBuilder();
            String line = null;

            line = br.readLine();


            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            br.close();
            strDataLengthList = sb.toString();
//			return sb.toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("testCodec", "surfaceCreated");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        Log.d("DecodeActivity", "in surfaceChanged");
        if (mPlayer == null) {
//            Toast.makeText(getApplicationContext(),
//                    "in surfaceChanged. creating playerthread",
//                    Toast.LENGTH_SHORT).show();
            mPlayer = new h265Decoder(holder.getSurface());
            mPlayer.start();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mPlayer != null) {
            mPlayer.interrupt();
        }
    }

    private class h264Decoder extends Thread {
        // private MediaExtractor extractor;
        private MediaCodec decoder;
        private Surface mSurface;

        public h264Decoder(Surface surface) {
            mSurface = surface;
        }

        @Override
        public void run() {
            h264_get_sps_pps();
            try {
//                        decoder = MediaCodec.createDecoderByType("video/avc");
                decoder = MediaCodec.createByCodecName("OMX.google.h264.decoder");

            } catch (IOException e) {
                e.printStackTrace();
            }

            MediaFormat mediaFormat = MediaFormat.createVideoFormat(
                    "video/avc", MediaCodecWidth, MediaCodecHeight);


            Log.e("testCodec", "header_sps.length=" + header_sps.length + ",header_pps len=" + header_pps.length);


            mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
            mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));

            decoder.configure(mediaFormat, mSurface /* surface */,
                    null /* crypto */, 0 /* flags */);

            if (decoder == null) {
                Log.e("testCodec", "Can't find video info!");
                return;
            }

            decoder.start();
//					Log.d("testCodec", "decoder.start() called");

            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();

//            long startMs = System.currentTimeMillis();

            int i = 0;
            while (!Thread.interrupted()) {

                if (i >= frames.size())
                    break;
                byte[] data = new byte[frames.get(i).frameData.length];
                System.arraycopy(frames.get(i).frameData, 0, data, 0,
                        frames.get(i).frameData.length);
//                Log.d("testCodec", "000000 i = " + i + " dataLength = " + frames.get(i).frameData.length);

                int inIndex = 0;
                while ((inIndex = decoder.dequeueInputBuffer(1)) < 0)
                    ;//判断解码器输入队列缓冲区有多少个buffer==，inIndex。
//                Log.e("testCodec", "111111 inIndex = " + inIndex);
                if (inIndex >= 0) {
                    ByteBuffer buffer = inputBuffers[inIndex];//取出解码器输入队列缓冲区最后一个buffer
                    buffer.clear();
                    int sampleSize = data.length;
                    if (sampleSize < 0) {
                        Log.d("testCodec",
                                "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        decoder.queueInputBuffer(inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        break;
                    } else {

//                        Log.d("testCodec", "222222 sample size: " + sampleSize);

                        buffer.clear();
                        buffer.put(data);
                        decoder.queueInputBuffer(inIndex, 0,
                                sampleSize, 0, 0);//
                    }

                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    int outIndex = decoder.dequeueOutputBuffer(info,
                            100000);//出队列缓冲区的信息
                    Log.e("testCodec",
                            "333333 outIndex " + outIndex + ", out Size=" + info.size);
                    switch (outIndex) {
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            Log.d("testCodec",
                                    "INFO_OUTPUT_BUFFERS_CHANGED");
                            outputBuffers = decoder.getOutputBuffers();
                            break;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.d("testCodec",
                                    "New format "
                                            + decoder.getOutputFormat());

                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Log.d("testCodec",
                                    "dequeueOutputBuffer timed out!");
                            try {
                                sleep(100);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            break;
                        default://outIndex>0
                            ByteBuffer outbuffer = outputBuffers[outIndex];

                            Log.e("testCodec",
                                    "555555 out Size=" + info.size);
//									byte[] outData = new byte[info.size];
//									outbuffer.get(outData);
//									saveFileToYUV_JPG_Bitmap(outData);

                            decoder.releaseOutputBuffer(outIndex, true);

                            break;
                    }
                    i++;


                }
            }

            decoder.stop();
            decoder.release();
        }
    }

    private class h265Decoder extends Thread {
        // private MediaExtractor extractor;
        private MediaCodec decoder;
        private Surface mSurface;

        public h265Decoder(Surface surface) {
            mSurface = surface;
        }

        @Override
        public void run() {

            byte[] header_vps_sps_pps = h265_get_vps_sps_pps();
            try {
//                        decoder = MediaCodec.createDecoderByType("video/hevc");
                decoder = MediaCodec.createByCodecName("OMX.google.hevc.decoder");

            } catch (IOException e) {
                Log.e("testCodec", "open fail+++++++++++++++++++");
                e.printStackTrace();
            }

            MediaFormat mediaFormat = MediaFormat.createVideoFormat(
                    "video/hevc", MediaCodecWidth, MediaCodecHeight);


            Log.e("testCodec", "header_vps_sps_pps.length=" + header_vps_sps_pps.length);


            mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(header_vps_sps_pps));
//                    mediaFormat.setString(MediaFormat.KEY_MIME, MIME_TYPE_HEVC);
//                    mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));

            decoder.configure(mediaFormat, mSurface /* surface */,
                    null /* crypto */, 0 /* flags */);

            if (decoder == null) {
                Log.e("testCodec", "Can't find video info!");
                return;
            }

            decoder.start();
//					Log.d("testCodec", "decoder.start() called");

            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();

            long startMs = System.currentTimeMillis();

            int i = 0;
            while (!Thread.interrupted()) {

//						long start = System.nanoTime();
                if (i >= frames.size())
                    break;
                byte[] data = new byte[frames.get(i).frameData.length];
                System.arraycopy(frames.get(i).frameData, 0, data, 0,
                        frames.get(i).frameData.length);
                Log.d("testCodec", "00000 i = " + i + " dataLength = " + frames.get(i).frameData.length);

                int inIndex = 0;
                while ((inIndex = decoder.dequeueInputBuffer(1)) < 0)
                    ;//判断解码器输入队列缓冲区有多少个buffer==，inIndex。

                if (inIndex >= 0) {
                    ByteBuffer buffer = inputBuffers[inIndex];//取出解码器输入队列缓冲区最后一个buffer
                    buffer.clear();
                    int sampleSize = data.length;
                    if (sampleSize < 0) {
                        Log.d("testCodec",
                                "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        decoder.queueInputBuffer(inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        break;
                    } else {


                        Log.d("testCodec", "sample size: "
                                + sampleSize);

//								buffer = ByteBuffer.allocate(data.length);
                        buffer.clear();
                        buffer.put(data);//向最后一个缓冲区inIndex中放入一帧数据。
                        decoder.queueInputBuffer(inIndex, 0,
                                sampleSize, 0, 0);//
                    }

                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    int outIndex = decoder.dequeueOutputBuffer(info,
                            100000);//出队列缓冲区的信息

                    switch (outIndex) {
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            Log.d("testCodec",
                                    "INFO_OUTPUT_BUFFERS_CHANGED");
                            outputBuffers = decoder.getOutputBuffers();
                            break;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.d("testCodec",
                                    "New format "
                                            + decoder.getOutputFormat());

                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Log.d("testCodec",
                                    "dequeueOutputBuffer timed out!");
                            try {
                                sleep(100);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            break;
                        default://outIndex>0
                            ByteBuffer outbuffer = outputBuffers[outIndex];

                            Log.e("testCodec",
                                    "We can't use this buffer but render it due to the API limit, "
                                            + outbuffer + ", out Size=" + info.size);
//									byte[] outData = new byte[info.size];
//									outbuffer.get(outData);
//                                    saveFileToYUV_JPG_Bitmap(outData);
//									long end = System.nanoTime();
//									long used = end - start;
//									Log.e("testCodec", "used:" + TimeUnit.NANOSECONDS.toMillis(used) + " ms");
                            decoder.releaseOutputBuffer(outIndex, true);
                            break;
                    }
                    i++;


                }
            }

            decoder.stop();
            decoder.release();

        }
    }

    /**
     *
     */
    private void h264_get_sps_pps() {
        int dataLength = readInData.length;
        int i = 0;

        while (i + 3 <= dataLength) {
            //sps
            if (readInData[i] == 0 && readInData[i + 1] == 0 && readInData[i + 2] == 0 && readInData[i + 3] == 1 && readInData[i + 4] == 0x67) {
                int j = i + 4;
                while (j < dataLength - 1 && !(readInData[j] == 0 && readInData[j + 1] == 0 && readInData[j + 2] == 0 && readInData[j + 3] == 1))
                    j++;
                header_sps = new byte[j - i];
                Arrays.fill(header_sps, (byte) 0);
                System.arraycopy(readInData, i, header_sps, 0, j - i);
                i = j;
                continue;
            }
            //pps
            if (readInData[i] == 0 && readInData[i + 1] == 0 && readInData[i + 2] == 0 && readInData[i + 3] == 1 && readInData[i + 4] == 0x68) {
                int j = i + 4;
                while (j < dataLength - 1 && !(readInData[j] == 0 && readInData[j + 1] == 0 && readInData[j + 2] == 0 && readInData[j + 3] == 1))
                    j++;
                header_pps = new byte[j - i];
                Arrays.fill(header_pps, (byte) 0);
                System.arraycopy(readInData, i, header_pps, 0, j - i);
                i = j;
                continue;
            }

            if (readInData[i] == 0 && readInData[i + 1] == 0 && readInData[i + 2] == 0 && readInData[i + 3] == 1 && (readInData[i + 4] == 0x65 || readInData[i + 4] == 0x41 || readInData[i + 4] == 0x61)) {
                int j = i + 4;
                while (j < dataLength - 1 && !(readInData[j] == 0 && readInData[j + 1] == 0 && readInData[j + 2] == 0 && readInData[j + 3] == 1))
                    j++;
                int frameLength = j - i - 4;
                Frame frame = new Frame(frameID);
//				frame.frameData = new byte[frameLength];
                frame.frameData = new byte[frameLength + 4];
                Arrays.fill(frame.frameData, (byte) 0);
//				System.arraycopy(readInData, i, frame.frameData, 0, frameLength);
                System.arraycopy(readInData, i, frame.frameData, 0, frameLength + 4);
//				Log.e("testCodec","start data i="+i+",frameLength="+frameLength+",frame.frameData[0]="+frame.frameData[0]+",frame.frameData[5]="+String.format("%02x", frame.frameData[5])+",final="+String.format("%02x", frame.frameData[frame.frameData.length-1]));
                frames.add(frame);
                frameID++;
                i = j;
                if (j == dataLength - 1) {
                    Log.e("000", "99999999");
                    break;
                }
                continue;
            }

            i++;
        }
    }

    private byte[] h265_get_vps_sps_pps() {
        int dataLength = readInData.length;
        int i = 0;
        byte[] header_vps = new byte[0];
//        byte[] header_else = new byte[0];
        while (i + 3 <= dataLength) {
            //vps
            if (readInData[i] == 0 && readInData[i + 1] == 0 && readInData[i + 2] == 0 && readInData[i + 3] == 1 && readInData[i + 4] == 0x40) {
                int j = i + 4;
                while (j < dataLength - 1 && !(readInData[j] == 0 && readInData[j + 1] == 0 && readInData[j + 2] == 0 && readInData[j + 3] == 1))
                    j++;
                header_vps = new byte[j - i];
                Arrays.fill(header_vps, (byte) 0);
                System.arraycopy(readInData, i, header_vps, 0, j - i);
                i = j;
                continue;
            }
            //sps
            if (readInData[i] == 0 && readInData[i + 1] == 0 && readInData[i + 2] == 0 && readInData[i + 3] == 1 && readInData[i + 4] == 0x42) {
                int j = i + 4;
                while (j < dataLength - 1 && !(readInData[j] == 0 && readInData[j + 1] == 0 && readInData[j + 2] == 0 && readInData[j + 3] == 1))
                    j++;
                header_sps = new byte[j - i];
                Arrays.fill(header_sps, (byte) 0);
                System.arraycopy(readInData, i, header_sps, 0, j - i);
                i = j;
                continue;
            }
            //pps
            if (readInData[i] == 0 && readInData[i + 1] == 0 && readInData[i + 2] == 0 && readInData[i + 3] == 1 && readInData[i + 4] == 0x44) {
                int j = i + 4;
                while (j < dataLength - 1 && !(readInData[j] == 0 && readInData[j + 1] == 0 && readInData[j + 2] == 0 && readInData[j + 3] == 1))
                    j++;
                header_pps = new byte[j - i];
                Arrays.fill(header_pps, (byte) 0);
                System.arraycopy(readInData, i, header_pps, 0, j - i);
                i = j;
                continue;
            }


            //else
//            if (readInData[i] == 0 && readInData[i + 1] == 0 && readInData[i + 2] == 0 && readInData[i + 3] == 1 && readInData[i + 4] == 0x4e) {
//                int j = i + 4;
//                while (j < dataLength - 1 && !(readInData[j] == 0 && readInData[j + 1] == 0 && readInData[j + 2] == 0 && readInData[j + 3] == 1))
//                    j++;
//                header_else = new byte[j - i];
//                Arrays.fill(header_else, (byte) 0);
//                System.arraycopy(readInData, i, header_else, 0, j - i);
//                i = j;
//                continue;
//            }

            if (readInData[i] == 0 && readInData[i + 1] == 0 && readInData[i + 2] == 0 && readInData[i + 3] == 1 && (readInData[i + 4] == 0x26 || readInData[i + 4] == 0x02)) {
                int j = i + 4;
                while (j < dataLength - 1 && !(readInData[j] == 0 && readInData[j + 1] == 0 && readInData[j + 2] == 0 && readInData[j + 3] == 1))
                    j++;
                int frameLength = j - i - 4;
                Frame frame = new Frame(frameID);
//				frame.frameData = new byte[frameLength];
                frame.frameData = new byte[frameLength + 4];
                Arrays.fill(frame.frameData, (byte) 0);
//				System.arraycopy(readInData, i, frame.frameData, 0, frameLength);
                System.arraycopy(readInData, i, frame.frameData, 0, frameLength + 4);

//				Log.e("testCodec","start data i="+i+",frameLength="+frameLength+",frame.frameData[0]="+frame.frameData[0]+",frame.frameData[5]="+String.format("%02x", frame.frameData[5])+",final="+String.format("%02x", frame.frameData[frame.frameData.length-1]));
                frames.add(frame);
                frameID++;
                i = j;
                if (j == dataLength - 1) {
                    Log.e("000", "99999999");
                    break;
                }
                continue;
            }

            i++;
        }
        //combine vps,sps,pps
        byte[] return_header = new byte[header_vps.length + header_sps.length + header_pps.length];
        Arrays.fill(return_header, (byte) 0);
        System.arraycopy(header_vps, 0, return_header, 0, header_vps.length);
        System.arraycopy(header_sps, 0, return_header, header_vps.length, header_sps.length);
        System.arraycopy(header_pps, 0, return_header, header_vps.length + header_sps.length, header_pps.length);
//        System.arraycopy(header_else, 0, return_header, header_vps.length+header_sps.length+ header_pps.length, header_else.length);

        return return_header;
    }

}