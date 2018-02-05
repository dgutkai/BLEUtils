package com.qcymall.ancdemo;

import android.content.Intent;
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
    }

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
                Log.e("BLEDetialActivity", String.format("Notify: %s", ByteUtils.byteToString(value)));
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


}
