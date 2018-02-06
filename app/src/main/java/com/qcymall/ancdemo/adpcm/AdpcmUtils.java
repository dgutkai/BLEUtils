package com.qcymall.ancdemo.adpcm;

/**
 * Created by lin on 2018/2/4.
 */

public class AdpcmUtils {

    public short valprev;
    public int index;

    static {
        System.loadLibrary("native-lib");

    }
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
        adpcmReset();
    }

    public native void adpcmReset();
    public native int adpcmCoder(byte[] indata, byte[] outdata, int len);
    public native int adpcmDecoder(byte[] indata, byte[] outdata, int len);
}
