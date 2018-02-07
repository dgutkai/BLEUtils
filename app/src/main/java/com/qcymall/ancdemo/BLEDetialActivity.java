package com.qcymall.ancdemo;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.inuker.bluetooth.library.Constants;
import com.inuker.bluetooth.library.connect.listener.BleConnectStatusListener;
import com.inuker.bluetooth.library.connect.options.BleConnectOptions;
import com.inuker.bluetooth.library.connect.response.BleConnectResponse;
import com.inuker.bluetooth.library.connect.response.BleNotifyResponse;
import com.inuker.bluetooth.library.connect.response.BleReadResponse;
import com.inuker.bluetooth.library.connect.response.BleUnnotifyResponse;
import com.inuker.bluetooth.library.connect.response.BleWriteResponse;
import com.inuker.bluetooth.library.model.BleGattProfile;
import com.inuker.bluetooth.library.utils.BluetoothLog;
import com.inuker.bluetooth.library.utils.BluetoothUtils;
import com.inuker.bluetooth.library.utils.ByteUtils;
import com.qcymall.ancdemo.adpcm.AdpcmUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import static com.inuker.bluetooth.library.Constants.REQUEST_SUCCESS;
import static com.inuker.bluetooth.library.Constants.STATUS_CONNECTED;
import static com.inuker.bluetooth.library.Constants.STATUS_DISCONNECTED;

/**
 * Created by lanmi on 2017/10/19.
 */

public class BLEDetialActivity extends BaseActivity {

    private String mMac;
    private String mName;
    private UUID mService;
    private UUID mCharacter;
    private MenuItem connectStatus;

    private byte[] adpcmBuff;
//    private byte[] audiobuff;
// 创建FileOutputStream对象
FileOutputStream outputStream = null;
    // 创建BufferedOutputStream对象
    BufferedOutputStream bufferedOutputStream = null;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bledetial);
        Intent intent = getIntent();
        mMac = intent.getStringExtra("mac");
        mName = intent.getStringExtra("name");
        mService = (UUID) intent.getSerializableExtra("service");
        mCharacter = (UUID) intent.getSerializableExtra("character");

        setTitle(mName);

        mBluetoothClien.registerConnectStatusListener(mMac, mBleConnectStatusListener);
        mBluetoothClien.notify(mMac, mService, mCharacter, mNotifyRsp);
        UUID writeUUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
        mBluetoothClien.write(mMac, mService, writeUUID, "1".getBytes(), new BleWriteResponse() {
            @Override
            public void onResponse(int code) {
                Log.e("OnResponse", "response = " + code);
            }
        });


        audioBufSize = AudioTrack.getMinBufferSize(4000,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        audioBufSize = 244*4;
        player = new AudioTrack(AudioManager.STREAM_MUSIC, 8000,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufSize,
                AudioTrack.MODE_STREAM);
//        audiobuff = new byte[audioBufSize*100];
        adpcmBuff = new byte[audioBufSize/4];
        player.play();
//        player1 = new Player();
//        player1.start();

//        String speakpath = "/sdcard/DCS/PCM/";
//        File file2 = new File(speakpath, "abc2.pcm");
//
//        // 如果文件存在则删除
//        if (file2.exists()) {
//            file2.delete();
//        }
//        // 在文件系统中根据路径创建一个新的空文件
//        try {
//            file2.createNewFile();
//            // 获取FileOutputStream对象
//            outputStream = new FileOutputStream(file2);
//            // 获取BufferedOutputStream对象
//            bufferedOutputStream = new BufferedOutputStream(outputStream);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }


//    @Override
//    public void onBackPressed() {
//        // 关闭创建的流对象
//        if (outputStream != null) {
//            try {
//                outputStream.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//        }
//        if (bufferedOutputStream != null) {
//            try {
//                bufferedOutputStream.close();
//            } catch (Exception e2) {
//                e2.printStackTrace();
//            }
//
//        }
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.status, menu);
        connectStatus = menu.findItem(R.id.menu_status);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.menu_status:
                if (mBluetoothClien.getConnectStatus(mMac) == Constants.STATUS_DEVICE_CONNECTED){
                    mBluetoothClien.disconnect(mMac);
                }else {
                    connectBLE();
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int status = mBluetoothClien.getConnectStatus(mMac);
        if (status == Constants.STATUS_DEVICE_DISCONNECTING || status == Constants.STATUS_DEVICE_DISCONNECTED){
            connectBLE();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothClien.unregisterConnectStatusListener(mMac, mBleConnectStatusListener);
    }

    private void connectBLE(){
        Log.e("BLEDetailActivity", "connectBLE");
        if (connectStatus != null) {
            connectStatus.setTitle(R.string.status_connecting);
        }
        BleConnectOptions options = new BleConnectOptions.Builder()
                .setConnectRetry(3)   // 连接如果失败重试3次
                .setConnectTimeout(30000)   // 连接超时30s
                .setServiceDiscoverRetry(3)  // 发现服务如果失败重试3次
                .setServiceDiscoverTimeout(20000)  // 发现服务超时20s
                .build();

        mBluetoothClien.connect(mMac, options, new BleConnectResponse() {
            @Override
            public void onResponse(int code, BleGattProfile data) {

                BluetoothLog.e("error code = " + code + ", GattProfile = " + data.toString());
            }
        });
    }

    private final BleConnectStatusListener mBleConnectStatusListener = new BleConnectStatusListener() {

        @Override
        public void onConnectStatusChanged(String mac, int status) {
            if (status == STATUS_CONNECTED) {
                connectStatus.setTitle(R.string.status_disconnect);

            } else if (status == STATUS_DISCONNECTED) {
                connectStatus.setTitle(R.string.status_connect);
            }
        }
    };

    private final BleReadResponse mReadRsp = new BleReadResponse() {
        @Override
        public void onResponse(int code, byte[] data) {
            if (code == REQUEST_SUCCESS) {
//                mBtnRead.setText(String.format("read: %s", ByteUtils.byteToString(data)));
                Log.e("BLEDetialActivity", String.format("read: %s", ByteUtils.byteToString(data)));

            }
        }
    };
    private final BleWriteResponse mWriteRsp = new BleWriteResponse() {
        @Override
        public void onResponse(int code) {
            if (code == REQUEST_SUCCESS) {
                Log.e("BLEDetialActivity", "success");
            } else {
                Log.e("BLEDetialActivity", "failed");
            }
        }
    };

    private final BleNotifyResponse mNotifyRsp = new BleNotifyResponse() {
        @Override
        public void onNotify(UUID service, UUID character, byte[] value) {
            if (service.equals(mService) && character.equals(mCharacter)) {
//                mBtnNotify.setText(String.format("%s", ByteUtils.byteToString(value)));
                Log.e("BLEDetialActivity", String.format("Notify: %s \n%d" , ByteUtils.byteToString(value), (new Date()).getTime() ));
                synchronized (BLEDetialActivity.this) {
                    byte[] resultData = new byte[value.length * 4];
                    int result2 = AdpcmUtils.shareInstance().adpcmDecoder(value, resultData, value.length);
                    player.write(resultData, 0, resultData.length);
                }

                // ========分割线==============
//                int cpylen = adpcmBuff.length-offset>value.length? value.length: adpcmBuff.length-offset;
//                System.arraycopy(value, 0, adpcmBuff, offset, cpylen);
//                offset += value.length;
//                if (offset >= adpcmBuff.length) {
//                    offset = 0;
//                    byte[] resultData = new byte[adpcmBuff.length * 4];
//                    int result2 = AdpcmUtils.shareInstance().adpcmDecoder(value, resultData, value.length);
//                    player.write(resultData, 0, resultData.length);
//                    Log.e("BLEDetialActivity", "playing");
//                }


                // ========分割线==============
//                try {
//                    // 往文件所在的缓冲输出流中写byte数据
//                    bufferedOutputStream.write(value);
//                    bufferedOutputStream.flush();
//
//                }catch (Exception e){
//
//                }


            }
        }

        @Override
        public void onResponse(int code) {
            if (code == REQUEST_SUCCESS) {
                Log.e("BLEDetialActivity", "success");
            } else {
                Log.e("BLEDetialActivity", "failed");
            }
        }
    };



    private int audioBufSize;
    Player player1;
    int offset ;
    private AudioTrack player;
    class Player extends Thread{
        byte[] data1=new byte[audioBufSize*2];
        File file=new File("/sdcard/DCS/PCM/abc2.pcm");
        int off1=0;
        FileInputStream fileInputStream;

        @Override
        public void run() {
            // TODO Auto-generated method stub
            super.run();

//                    String speakpath = "/sdcard/DCS/PCM/";
//                    File file2 = new File(speakpath, "abc2.pcm");
//                    // 创建FileOutputStream对象
//                    FileOutputStream outputStream = null;
//                    // 创建BufferedOutputStream对象
//            BufferedOutputStream bufferedOutputStream = null;
//                    // 如果文件存在则删除
//                    if (file2.exists()) {
//                        file2.delete();
//                    }
//                    // 在文件系统中根据路径创建一个新的空文件
//            try {
//                file2.createNewFile();
//                // 获取FileOutputStream对象
//                outputStream = new FileOutputStream(file2);
//                // 获取BufferedOutputStream对象
//                bufferedOutputStream = new BufferedOutputStream(outputStream);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            int offset_thread = 0;
            while(true){
                try {
                    fileInputStream=new FileInputStream(file);
                    fileInputStream.skip((long)off1);
                    int result = fileInputStream.read(data1,0,audioBufSize*2);
                    off1 +=audioBufSize*2;
                    if (result <= 0){
                        player.stop();
                        player.release();
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
//
//                AdpcmUtils.AdpcmState state = new AdpcmUtils.AdpcmState();
////                byte[] bytes = new byte[audioBufSize/4];
//                byte[] resultData = new byte[audioBufSize];
////                int result = AdpcmUtils.shareInstance().adpcmCoder(data1, bytes, data1.length);
//                int result2 = AdpcmUtils.shareInstance().adpcmDecoder(data1, resultData, data1.length);
//                player.write(resultData, offset, resultData.length);
                short[] shorts = toShortArray(data1);
                player.write(shorts, offset, shorts.length);

//                try {
//                    // 往文件所在的缓冲输出流中写byte数据
//                    bufferedOutputStream.write(resultData);
//                    bufferedOutputStream.flush();
//                }catch (Exception e){
//
//                }
                Log.e("Tag", "data1.length" + data1.length);
//                player.write(audiobuff, offset_thread, audioBufSize);
//                offset_thread += audioBufSize;
            }
            // 关闭创建的流对象
//                    if (outputStream != null) {
//                        try {
//                            outputStream.close();
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//
//                    }
//                    if (bufferedOutputStream != null) {
//                        try {
//                            bufferedOutputStream.close();
//                        } catch (Exception e2) {
//                            e2.printStackTrace();
//                        }
//
//                    }
        }
    }

    public static short[] toShortArray(byte[] src) {

        int count = src.length >> 1;
        short[] dest = new short[count];
        for (int i = 0; i < count; i++) {
            dest[i] = (short) (src[i * 2 + 1] << 8 | src[2 * i] & 0xff);
        }
        return dest;
    }

    public static byte[] toByteArray(short[] src) {

        int count = src.length;
        byte[] dest = new byte[count << 1];
        for (int i = 0; i < count; i++) {
            dest[i * 2 + 1] = (byte) (src[i] >> 8);
            dest[i * 2 ] = (byte) (src[i] >> 0);
        }

        return dest;
    }
}
