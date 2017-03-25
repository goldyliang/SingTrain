package com.singtrain;

import android.content.Context;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

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

    public TrainDetector() {
    }

    public void startDetect(Context context, File assetDir, TrainDetectListener listener) {
        this.listener = listener;

        AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(16000, 1024, 0);//22050,1024,0);

        outFile = new File(context.getFilesDir(), "record.out");

        System.out.println("Output file:" + outFile.getAbsolutePath());
        //final PrintWriter writer;

        //syllableOutFile = new File (getApplicationContext().getFilesDir(), "syllable.out");

        try {
            //writer = new PrintWriter(file);
            writer = new PrintWriter(outFile);
            //writer= new PrintWriter(System.out);
            //syllableOutWriter=new PrintWriter(System.out);
        } catch (Exception e) {
            return;
        }

        PitchDetectionHandler pdh = new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult result, AudioEvent e) {
                final float pitchInHz = result.getPitch();
                final long time = System.currentTimeMillis();
                System.out.println("Pitch," + String.valueOf(time) + "," + String.valueOf(pitchInHz));//)%d,%f")
                //writer.printf("%d,%f", time, pitchInHz);
                        /*runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView text = (TextView) findViewById(R.id.result_text);
                                text.setText( " " + pitchInHz);
                                //System.currentTimeMillis()
                            }
                        }); */
            }
        };
        AudioProcessor p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, pdh);
        dispatcher.addAudioProcessor(p);
        new Thread(dispatcher, "Audio Dispatcher").start();

        AudioRecordFromDispatcher recordFromDispatcher = new AudioRecordFromDispatcher(dispatcher, 6, 16000, 16, 2, 6400 * 2);
        //22050, 16, 2, 6400 * 2);

        try {
            setupRecognizer(assetDir);

            // Replace the recorder with the one from dispatcher
            Field f = recognizer.getClass().getDeclaredField("recorder");
            f.setAccessible(true);
            f.set(recognizer, recordFromDispatcher);
        } catch (IOException e) {
            return;
        } catch (NoSuchFieldException e) {
            return;
        } catch (IllegalAccessException e) {
            return;
        }

        recognizer.startListening(SYLLABLES_SEARCH, 10000);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .setRawLogDir(assetsDir)

                // Threshold to tune for keyphrase to balance between false alarms and misses
                //.setKeywordThreshold(1e-45f)

                // Use context-independent phonetic search, context-dependent is too slow for mobile
                .setBoolean("-allphone_ci", true)

                .getRecognizer();
        recognizer.addListener(this);

        /** In your application you might not need to add all those searches.
         * They are added here for demonstration. You can leave just one.
         */

        File syllableGrammar = new File(assetsDir, "syllables.gram");
        recognizer.addGrammarSearch(SYLLABLES_SEARCH, syllableGrammar);
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

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
            //System.out.println("Syllable," + String.valueOf(time) + "," + syllable);
            writer.printf("Syllable,%d,%s", time, syllable);
            listener.onSyllable(syllable);
        }

        lastCached = time;
        cacheResult = text;
    }

    public void handlePitch(PitchDetectionResult result, AudioEvent e) {
        final float pitchInHz = result.getPitch();
        final long time = System.currentTimeMillis();
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

    }
}
