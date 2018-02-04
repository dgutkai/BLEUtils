package com.qcymall.ancdemo.adpcm;

/**
 * Created by lin on 2018/2/4.
 */

public class AdpcmUtils {

    public short valprev;
    public int index;

    public static class AdpcmState{
        public short valprev;
        public int index;
    }
    private static AdpcmUtils instance;
    public static AdpcmUtils shareInstance(){
        if (instance == null){
            instance = new AdpcmUtils();
        }
        return instance;
    }
    private AdpcmUtils(){
        System.loadLibrary("native-lib");
    }

    public native int addFromJNI();
    public native int adpcmCoder(short[] indata, byte[] outdata, int len, short valprev, int index);
}
