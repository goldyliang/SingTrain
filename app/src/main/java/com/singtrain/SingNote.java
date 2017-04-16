package com.singtrain;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Environment;

import com.midisheetmusic.R;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.MultichannelToMono;
import be.tarsos.dsp.WaveformSimilarityBasedOverlapAdd;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.TarsosDSPAudioInputStream;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.io.android.AndroidAudioInputStream;
import be.tarsos.dsp.resample.RateTransposer;

/**
 * Created by elnggng on 4/16/17.
 */

public class SingNote {
    public int noteNumber;
    public String noteName;
    public int durationMSec;

    private final static int clipValidLength = 280; //280 ms
    private final static int clipTotalLength = 370; //370 ms

    private final static int bufferSizeInSecs = 6;

    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private static final String AUDIO_RECORDER_FOLDER = "AudioRecorder";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.wav";
    private static final int RECORDER_SAMPLERATE = 16000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException
    {
        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }

    private String getTempFilename() {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }

        File tempFile = new File(filepath,AUDIO_RECORDER_TEMP_FILE);

        if (tempFile.exists())
            tempFile.delete();

        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
    }

    private static double centToFactor(double cents){
        return 1 / Math.pow(Math.E,cents*Math.log(2)/1200/Math.log(Math.E));
    }

    private static int baseNumberFromName (String name) {
        switch (name.toLowerCase()) {
            case "do":
                return 48;
            case "re":
                return 50;
            case "mi":
                return 52;
            case "fa":
                return 53;
            case "so":
                return 55;
            case "la":
                return 57;
            case "ti":
                return 59;
        }
        return -1;
    }

    public int getStandardSoundResId() {
        int origNumber = baseNumberFromName(noteName);

        //if (noteNumber == origNumber) {
            try {
                Field f = R.raw.class.getField("note_" + noteName.toLowerCase());
                f.setAccessible(true);
                int resId = f.getInt(null);

                return resId;
            } catch  (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
                return -1;
            }
        //} else
        //    return -1;
    }

    public String generateSound(Context context) {

        int origNumber = baseNumberFromName(noteName);

        InputStream inputStream;

        int clipLength;

        try {
            Field f = R.raw.class.getField("note_" + noteName.toLowerCase());
            f.setAccessible(true);
            int resId = f.getInt(null);

            inputStream = context.getResources().openRawResource(resId);
            clipLength = (int)context.getResources().openRawResourceFd(resId).getLength();
        } catch (Resources.NotFoundException | NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }

        final ByteBuffer buffer = ByteBuffer.allocate(clipLength);

        TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(16000, 16,1, true, false);

        TarsosDSPAudioInputStream audioStream = new UniversalAudioInputStream(inputStream, format);

        double factor = centToFactor( (noteNumber - origNumber) * 100);

        WaveformSimilarityBasedOverlapAdd wsola = new WaveformSimilarityBasedOverlapAdd(
                WaveformSimilarityBasedOverlapAdd.Parameters.musicDefaults(factor, RECORDER_SAMPLERATE));

        AudioDispatcher dispatcher = new AudioDispatcher(audioStream, wsola.getInputBufferSize(),wsola.getOverlap());

        wsola.setDispatcher(dispatcher);

        dispatcher.addAudioProcessor(wsola);

        RateTransposer rateTransposer = new RateTransposer(factor);

        dispatcher.addAudioProcessor(rateTransposer);

        AudioProcessor saver = new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                byte[] rawBuffer = audioEvent.getByteBuffer();
                int lengthToPut = Math.min(buffer.remaining(), rawBuffer.length);
                System.out.println("Received: " + rawBuffer.length + " Put: " + lengthToPut);
                buffer.put(rawBuffer, 0, lengthToPut);

                return true;
            }

            @Override
            public void processingFinished() {
            }
        };

        dispatcher.addAudioProcessor(saver);

        String filename = getTempFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }

        Thread p = new Thread(dispatcher);
        p.start();

        try {
            p.join();
        } catch (InterruptedException e) {
            System.out.println("Error " + e.getMessage());
            return null;
        }

        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * 1/8;

        try {
            WriteWaveFileHeader(os, buffer.position(), buffer.position() + 36,
                    RECORDER_SAMPLERATE, 1, byteRate);
            os.write(buffer.array(), 0, buffer.position());

            inputStream.close();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return filename;

    }

}
