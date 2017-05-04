package com.geely.dilan.testble;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.geely.dilan.testble.ble.BleManager;

import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private BleManager bleManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bleManager = new BleManager(this, new BleManager.OnIBeaconListener() {
            @Override
            public void onFindIBeacon(BleManager.IBeacon beacon) {
                Log.d("onFindIBeacon", "beacon---> name:" + beacon.name + "  mac:" + beacon.bluetoothAddress);
                bleManager.stopLeScan();
                Log.d("onFindIBeacon", "format---> " + bleManager.formatBeaconData(beacon));
            }

            @Override
            public void onComplete(List<BleManager.IBeacon> iBeacons) {

            }

            @Override
            public void onError(String error) {

            }
        });

        findViewById(R.id.btn_start).setOnClickListener(this);
        findViewById(R.id.btn_stop).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start:
                bleManager.startLeScan(10);
                break;

            case R.id.btn_stop:
                bleManager.stopLeScan();
                break;

            default:
                break;
        }
    }
}
