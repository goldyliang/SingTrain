package com.singtrain;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Iterator;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.android.AndroidAudioInputStream;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.Segment;
import edu.cmu.pocketsphinx.SegmentList;
import edu.cmu.pocketsphinx.SpeechRecognizer;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;
import android.media.MediaRecorder.AudioSource;

import android.media.audiofx.AcousticEchoCanceler;

/**
 * Created by Liang on 2017-03-24.
 */

public class TrainDetector implements RecognitionListener, PitchDetectionHandler {

    private static final String SYLLABLES_SEARCH = "syllables";

    private TrainDetectListener listener;
    private File outFile;
    private PrintWriter writer;
    private SpeechRecognizer recognizer;

    private String cacheResult = "";
    private long lastCached = 0;
    private int lastFrame = -1;

    private Decoder decoder;

    //private long startTime = 0;

    private AudioDispatcher dispatcher;
    private AudioRecordFromDispatcher recordFromDispatcher = null;

    /* Frame per second */
    private double frameRate;// = 16000 / 1024;

    private MediaRecorder mRecorder = null;


    public TrainDetector() {
    }

    private AudioDispatcher getDispatcherFromMicrophone(int source, int samepleRate, int buffSize) {
        int var3 = AudioRecord.getMinBufferSize(samepleRate, 16, 2);
        int var4 = var3 / 2;
        if(var4 <= buffSize) {
            AudioRecord var5 = new AudioRecord(source, samepleRate, 16, 2, buffSize * 2);
            TarsosDSPAudioFormat var6 = new TarsosDSPAudioFormat((float)samepleRate, 16, 1, true, false);
            AndroidAudioInputStream var7 = new AndroidAudioInputStream(var5, var6);

            //System.out.println("UNPROCESSED = " + AudioManager.getProperty("PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED"));

            /*boolean isAvailable = AcousticEchoCanceler.isAvailable();
            if (isAvailable) {
                AcousticEchoCanceler aec = AcousticEchoCanceler.create(var5.getAudioSessionId());
                if(!aec.getEnabled())
                    aec.setEnabled(true);
                System.out.println(" AEC enabled : " + aec.getEnabled() + " . Has control: " + aec.hasControl());
            }
            else
                System.out.println(" AEC is not available");*/

            var5.startRecording();
            return new AudioDispatcher(var7, buffSize, 0);
        } else {
            throw new IllegalArgumentException("Buffer size too small should be at least " + var3 * 2);
        }
    }

    public void startDetect(int source, Context context, File assetDir, TrainDetectListener listener, String outFile) {
        this.listener = listener;

        //dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(16000, 1024, 0);//22050,1024,0);
        dispatcher = getDispatcherFromMicrophone(source, 16000, 1024);//22050,1024,0);

        /*int var3 = AudioRecord.getMinBufferSize(16000, 16, 2);
        int var4 = var3 / 2;
        if(var4 <= 1024) {
            AudioRecord var5 = new AudioRecord(1, 16000, 16, 2, 1024 * 2);
            TarsosDSPAudioFormat var6 = new TarsosDSPAudioFormat((float)16000, 16, 1, true, false);
            AndroidAudioInputStream var7 = new AndroidAudioInputStream(var5, var6);
            var5.startRecording();
            dispatcher = new AudioDispatcher(var7, 1024, 0);
        } else {
            throw new IllegalArgumentException("Buffer size too small should be at least " + var3 * 2);
        }*/

        //outFile = new File(context.getFilesDir(), "record.out");

        //System.out.println("Output file:" + outFile.getAbsolutePath());
        //final PrintWriter writer;

        //syllableOutFile = new File (getApplicationContext().getFilesDir(), "syllable.out");

        try {
            //writer = new PrintWriter(file);
            //writer = new PrintWriter(outFile);
            //writer= new PrintWriter(System.out);
            //syllableOutWriter=new PrintWriter(System.out);
        } catch (Exception e) {
            return;
        }

        AudioProcessor p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 16000, 1024, this);
        dispatcher.addAudioProcessor(p);
        new Thread(dispatcher, "Audio Dispatcher").start();

        recordFromDispatcher = new AudioRecordFromDispatcher(dispatcher, 6, 16000, 16, 2, 6400 * 2);
        //22050, 16, 2, 6400 * 2);

        //startTime = System.currentTimeMillis();
        //System.out.println("Start Time:" + startTime);

        try {
            setupRecognizer(assetDir);

            // Replace the recorder with the one from dispatcher
            Field f = recognizer.getClass().getDeclaredField("recorder");
            f.setAccessible(true);
            f.set(recognizer, recordFromDispatcher);

            f = recognizer.getClass().getDeclaredField("decoder");
            f.setAccessible(true);
            decoder = (Decoder)f.get(recognizer);

            frameRate = decoder.getConfig().getInt("-frate");
            System.out.println ("Frame rate:" + frameRate);

            /*f = recognizer.getClass().getDeclaredField("bufferSize");
            f.setAccessible(true);

            int bufSize = (int)f.get(recognizer);

            frameRate = bufSize / 16000f;*/
        } catch (IOException e) {
            return;
        } catch (NoSuchFieldException e) {
            return;
        } catch (IllegalAccessException e) {
            return;
        }

        recognizer.startListening(SYLLABLES_SEARCH, 10000);

        /*mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(AudioSource.REMOTE_SUBMIX);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(outFile);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            System.out.println( "prepare() failed");
        }

        mRecorder.start();*/

    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        if (recognizer == null) {
            recognizer = defaultSetup()
                    .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                    .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                    // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                    .setRawLogDir(assetsDir)

                    // Threshold to tune for keyphrase to balance between false alarms and misses
                    //.setKeywordThreshold(1e-45f)

                    // Use context-independent phonetic search, context-dependent is too slow for mobile
                    .setBoolean("-allphone_ci", true)
                    //.setBoolean("-time", true)
                    //.setString("-time", "yes")
                    .getRecognizer();
            recognizer.addListener(this);

            /** In your application you might not need to add all those searches.
             * They are added here for demonstration. You can leave just one.
             */

            File syllableGrammar = new File(assetsDir, "syllables.gram");
            recognizer.addGrammarSearch(SYLLABLES_SEARCH, syllableGrammar);
        }
    }

    private long frameNumToTime (int fnum) {
        return  recordFromDispatcher.getStartTime() + Math.round(fnum * 1000f / frameRate);
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        /*SegmentList list = decoder.seg();
        Iterator<Segment> iter = list.iterator();

        Segment seg = null;
        while (iter.hasNext()) {
            seg = iter.next();

            //if (seg.getStartFrame() >= lastFrame) {
                //if (!seg.getWord().contains("<sil>") && !seg.getWord().contains("NULL")) {

                    System.out.println("SEG:" +
                                    frameNumToTime(seg.getStartFrame()) + "-" +
                                    frameNumToTime(seg.getEndFrame()) + "," +
                                    seg.getWord() + "," +
                                    seg.getAscore() + "," +
                                    seg.getLscore() + "," +
                                    seg.getProb());
                //}

             //   lastFrame = seg.getEndFrame();
            //}
        } */

        // seg is the last


        String text = hypothesis.getHypstr();

        if (text.length() <= cacheResult.length())
            return;

        int indx = text.indexOf(cacheResult);

        long time = System.currentTimeMillis();
        String syllable = "";
        if (indx==0) {
            // We may get a new syllable
            if (text.length() - cacheResult.length() > 1) {
                time = System.currentTimeMillis();
                syllable = text.substring(cacheResult.length());
            }
        } else {
            // We may get a new result of the previous recognized syllable
            int i = cacheResult.lastIndexOf(' ');
            if (i<0) {
                cacheResult = " " + cacheResult;
                i = 0;
            }
            String tmpText =  text;
            int j = text.lastIndexOf(' ');
            if (j<0) {
                tmpText = " " + text;
                j = 0;
            }

            if (cacheResult.substring(0,i).equals(tmpText.substring(0,j))) {
                time = lastCached;
                syllable = tmpText.substring(j+1);
            }
        }

        if (syllable.length() > 0) {
            System.out.println("Syllable," + String.valueOf(time) + "," + syllable);
            //writer.printf("Syllable,%d,%s", time, syllable);
            String[] syllables = syllable.split(" ");

            for (String singleSyllable : syllables)
                listener.onSyllable(singleSyllable);
        }

        lastCached = time;
        cacheResult = text;
    }

    @Override
    public void handlePitch(PitchDetectionResult result, AudioEvent e) {

        final float pitchInHz = result.getPitch();
        final long time = System.currentTimeMillis();
        System.out.println("Pitch," + String.valueOf(time) + "," + String.valueOf(pitchInHz));//)%d,%f")
        //writer.printf("%d,%f", time, pitchInHz);

        listener.onPitch(pitchInHz);
    }


    @Override
    public void onResult(Hypothesis hypothesis) {
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onEndOfSpeech() {
    }

    @Override
    public void onTimeout() {
    }

    @Override
    public void onError(Exception error) {
    }

    public void stopDetect() {
        recognizer.stop();
        dispatcher.stop();
        //mRecorder.stop();
    }
}
