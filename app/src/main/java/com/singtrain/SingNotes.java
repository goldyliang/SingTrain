package com.singtrain;

/**
 * Created by elnggng on 4/3/17.
 */

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.util.HashMap;

public class SingNotes {

    private static SoundPool soundPool;

//    private static HashMap<String, Integer> soundPoolMap;

    private static HashMap<SingNote, Integer> noteMap;

    //private static AudioTrack audioTrack;

    //private final static int bufferSizeInSecs = 6;

    //private final static int sampleRate = 16000;

    /** Populate the SoundPool*/

 /*   public static boolean isInit() {
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
    } */

    public static void init () {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder().setAudioAttributes(attributes).build();

        noteMap = new HashMap();

        //int bufferSizeInBytes = bufferSizeInSecs * sampleRate * 2;
        /*audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes,AudioTrack.MODE_STREAM);*/
    }



    public static void loadNote(Context context, SingNote note) {

        int resId = note.getStandardSoundResId();

        int soundId = 0;

        if (resId > 0) {
            soundId = soundPool.load(context, resId, 1);
            noteMap.put(note, soundId);
        } else {
            //String tmpFile = note.generateSound(context);
            //if (tmpFile != null) {
            //    soundId = soundPool.load(tmpFile, 1);
            //}
        }

    }

    public static void playNote(Context context, SingNote note, float volume) {

        if (!noteMap.containsKey(note)) {
            loadNote (context, note);
        }

        if (noteMap.containsKey(note)) {
            soundPool.play(noteMap.get(note), volume, volume, 100, 0, 1);
        } else
            System.out.println ("Can not play " + note.toString());
    }


}
