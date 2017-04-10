package com.singtrain;

/**
 * Created by elnggng on 4/3/17.
 */

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;

import com.midisheetmusic.R;

import java.lang.reflect.Field;
import java.util.HashMap;

import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.SpeechRecognizer;

public class SyllableScales {
    private static int noteSounds[];

    private static SoundPool soundPool;

    private static HashMap<String, Integer> soundPoolMap;

    /** Populate the SoundPool*/

    public static boolean isInit() {
        return soundPoolMap != null && soundPool != null;
    }

    public static void init(Context context) throws IllegalAccessException {

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder().setAudioAttributes(attributes).build();

        soundPoolMap = new HashMap();

        for (Field f : R.raw.class.getDeclaredFields()) {
            if (f.getName().startsWith("note_")) {
                f.setAccessible(true);
                int resId = f.getInt(null);
                int soundId = soundPool.load(context, resId, 1);
                String noteName = f.getName().split("_")[1];
                soundPoolMap.put(noteName, soundId);
            }
        }
    }

    public static void playNote(String note, float volume) {
        String name = note.toLowerCase();

        if (soundPoolMap.containsKey(name)) {
            soundPool.play(soundPoolMap.get(name), volume, volume, 100, 0, 1);
        }
    }


}
