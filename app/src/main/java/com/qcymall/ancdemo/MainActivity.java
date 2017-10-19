package com.qcymall.ancdemo;

import android.content.Intent;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.inuker.bluetooth.library.BluetoothClient;
import com.inuker.bluetooth.library.beacon.Beacon;
import com.inuker.bluetooth.library.connect.listener.BluetoothStateListener;
import com.inuker.bluetooth.library.search.SearchRequest;
import com.inuker.bluetooth.library.search.SearchResult;
import com.inuker.bluetooth.library.search.response.SearchResponse;
import com.inuker.bluetooth.library.utils.BluetoothLog;
import com.inuker.bluetooth.library.utils.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends BaseActivity {

    private SwipeRefreshLayout refreshLayout;
    private ListView deviceListView;
    private SimpleAdapter deviceAdapter;
    private ArrayList<HashMap<String, Object>> deviceData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initDeviceListView();
        scanBleDevice();
    }

    private void checkBluetooth(){
        if (!mBluetoothClien.isBluetoothOpened()){
            mBluetoothClien.openBluetooth();
            mBluetoothClien.registerBluetoothStateListener(mBluetoothStateListener);

        }
    }
    private void initDeviceListView(){
        refreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipelayout);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                scanBleDevice();
            }
        });
        deviceListView = (ListView) findViewById(R.id.devicelist);
        deviceData = new ArrayList<HashMap<String, Object>>();
        deviceAdapter = new SimpleAdapter(this, deviceData, R.layout.item_devicelist, new String[]{"name", "mac"},
                new int[]{R.id.name, R.id.mac});
        deviceListView.setAdapter(deviceAdapter);
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Intent detialIntent = new Intent(getBaseContext(), BLEDetialActivity.class);
                detialIntent.putExtra("data", deviceData.get(i));
                startActivity(detialIntent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
//        checkBluetooth();
    }

    @Override
    protected void onPause() {
        super.onPause();
//        mBluetoothClien.stopSearch();
        mBluetoothClien.unregisterBluetoothStateListener(mBluetoothStateListener);
    }

    private final BluetoothStateListener mBluetoothStateListener = new BluetoothStateListener() {
        @Override
        public void onBluetoothStateChanged(boolean openOrClosed) {
            if (openOrClosed){
                scanBleDevice();
            }
            mBluetoothClien.unregisterBluetoothStateListener(mBluetoothStateListener);
        }

    };

    private void scanBleDevice(){
        deviceData.clear();
        deviceAdapter.notifyDataSetChanged();
        refreshLayout.setRefreshing(true);
        SearchRequest request = new SearchRequest.Builder()
                .searchBluetoothLeDevice(3000, 3)   // 先扫BLE设备3次，每次3s
//                .searchBluetoothClassicDevice(5000) // 再扫经典蓝牙5s
//                .searchBluetoothLeDevice(2000)      // 再扫BLE设备2s
                .build();

        mBluetoothClien.search(request, new SearchResponse() {
            @Override
            public void onSearchStarted() {

            }

            @Override
            public void onDeviceFounded(SearchResult device) {
                Beacon beacon = new Beacon(device.scanRecord);
                if (!isHaveDevice(device.getAddress())) {
                    HashMap<String, Object> data = new HashMap<String, Object>();
                    data.put("name", device.getName());
                    data.put("mac", device.getAddress());
                    deviceData.add(data);
                    deviceAdapter.notifyDataSetChanged();
                    BluetoothLog.e(String.format("beacon for %s\n%s", device.getAddress(), beacon.toString()));
                }
            }

            @Override
            public void onSearchStopped() {
                refreshLayout.setRefreshing(false);
            }

            @Override
            public void onSearchCanceled() {
                refreshLayout.setRefreshing(false);
            }
        });
    }

    private boolean isHaveDevice(String mac){
        if (mac == null){
            return false;
        }
        for (HashMap<String, Object> data:  deviceData) {
            if (mac.equals(data.get("mac"))){
                return true;
            }
        }
        return false;
    }
}
