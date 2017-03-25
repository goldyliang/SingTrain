package com.singtrain;

/**
 * Created by Liang on 2017-03-24.
 */

public interface TrainDetectListener {
    void onPitch(float pitchInHz);
    void onSyllable(String syllable);
}
