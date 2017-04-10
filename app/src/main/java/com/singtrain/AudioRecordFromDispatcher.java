package com.singtrain;

import android.media.AudioRecord;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;

/**
 * Created by Liang on 2017-03-09.
 */

public class AudioRecordFromDispatcher extends AudioRecord {

    private boolean started;
    private BlockingDeque<byte[]> queue = new LinkedBlockingDeque<>();
    private AudioDispatcher dispatcher;
    private AudioProcessor processor;

    private int sampleRate;
    private int channelCfg;
    private int audioFmt;

    private long startTime = 0;

    public long getStartTime() {
        return startTime;
    }

    public AudioRecordFromDispatcher(
            AudioDispatcher dispatcher, int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes ) {
        super(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
        this.dispatcher = dispatcher;
        sampleRate = sampleRateInHz;
        channelCfg = channelConfig;
        audioFmt = audioFormat;
    }

    @Override
    public int read(short[] audioData, int offsetInShorts, int sizeInShorts) {
        try {
            Arrays.fill(audioData,(short)-1);

            int len = 0; // size of shorts already received
            while (len < sizeInShorts) {
                byte[] buffer = queue.takeFirst();
                int tlen = buffer.length / 2;
                if ( len + tlen > sizeInShorts) {
                    byte[] remain = new byte[ (len + tlen - sizeInShorts) * 2];
                    System.arraycopy(buffer, buffer.length - remain.length, remain, 0, remain.length);
                    queue.putFirst(remain);
                    tlen = sizeInShorts - len;
                    //throw new IllegalStateException("Buffer too big " + buffer.length + ", " + sizeInShorts);
                }
                // to turn bytes to shorts as either big endian or little endian.
                ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().
                        get(audioData, len, tlen);

                len += tlen;
            }
            return len;
        } catch (InterruptedException | IllegalStateException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    @Override
    public void startRecording() throws IllegalStateException {
        if (started)
            return;

        processor = new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                if (audioEvent.getSampleRate() != sampleRate)
                    throw new IllegalStateException("Not correct sample rate");

                if (startTime == 0) {
                    startTime = System.currentTimeMillis();
                    System.out.println("Start time: " + startTime);
                }

                byte[] buffer = audioEvent.getByteBuffer();
                int size = audioEvent.getBufferSize();
                try {
                    queue.putLast (buffer);
                } catch (InterruptedException e) {
                    return false;
                }
                return true;
            }

            @Override
            public void processingFinished() {

            }
        };

        dispatcher.addAudioProcessor(processor);
        started = true;
    }

    @Override
    public void stop() throws IllegalStateException {
        if (started) {
            dispatcher.removeAudioProcessor(processor);
            started = false;
        }
    }

    @Override
    public int getRecordingState() {
        return started ? 0 : 1;
    }

}
