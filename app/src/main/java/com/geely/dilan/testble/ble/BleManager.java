package com.geely.dilan.testble.ble;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BleManager implements LeScanCallback {

    private final String TAG = getClass().getSimpleName();

    /**
     * 搜索BLE终端
     */
    private BluetoothAdapter mBluetoothAdapter;
    private OnIBeaconListener iBeaconListener;
    private Activity mActivity;
    private final Handler mHandler;
    private boolean mScanning;
    private int duration;
    private List<IBeacon> iBeacons = new ArrayList<>();
    private Runnable scanRunnable;

    public BleManager(@NonNull Activity activity, OnIBeaconListener onIBeaconListener) {
        mActivity = activity;
        // 获取BLE管理器
        BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        // 获取搜索BLE终端
        mBluetoothAdapter = bluetoothManager.getAdapter();

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                startLeScan(duration);
                unregisterBluetooth();
            }
        };

        iBeaconListener = onIBeaconListener;
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "~ Stopping Scan (timeout)");
                mScanning = false;
                mBluetoothAdapter.stopLeScan(BleManager.this);
                if (iBeaconListener != null) {
                    if (iBeacons.size() > 0) {
                        iBeaconListener.onComplete(iBeacons);
                    } else {
                        iBeaconListener.onError("蓝牙信号错误，或不在可还车区域\n" +
                                "建议调整您的位置或手机条件后重试");
                    }
                }
            }
        };
    }

    public void registerBluetooth() {
        Log.d(TAG, "~ registerBluetooth");
        mActivity.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    private void unregisterBluetooth() {
        Log.d(TAG, "~ unregisterBluetooth");
        try {
            mActivity.unregisterReceiver(mReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 监听蓝牙状态
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                if (BluetoothAdapter.STATE_ON == intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR)) {
                    mHandler.sendEmptyMessageDelayed(0, 100);
                }
            }
        }
    };

    public void startLeScan(final int seconds) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 || !isBluetoothLeSupported()) {
            if (iBeaconListener != null) {
                iBeaconListener.onError("该手机不支持蓝牙4.0");
            }
            return;
        }

        duration = seconds;
        if (isBluetoothEnabled()) {
            if (mScanning) {
                return;
            }
            Log.d(TAG, "~ Starting Scan");
            // Stops scanning after a pre-defined scan period.
            if (seconds > 0) {
                iBeacons.clear();
                mHandler.postDelayed(scanRunnable, seconds * 1000);
            }
            mScanning = true;
            try {
                mBluetoothAdapter.startLeScan(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
//            if (iBeaconListener != null) {
//                iBeaconListener.onError("蓝牙未开启");
//            }

            openBluetooth();
            registerBluetooth();
        }
    }

    public void stopLeScan() {
        mScanning = false;
        mHandler.removeCallbacks(scanRunnable);
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.stopLeScan(this);
        }
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        IBeacon ibeacon = formatScanData(device, rssi, scanRecord);
        if (ibeacon != null) {
            if (iBeaconListener != null) {
                iBeaconListener.onFindIBeacon(ibeacon);
                if (duration > 0) {
                    iBeacons.add(ibeacon);
                }
            }
        }
    }

    private boolean isBluetoothLeSupported() {
        return mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    private void openBluetooth() {
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.enable();
        }
    }

    // check BlueTooth has been opened whether or not
    private boolean isBluetoothEnabled() {
        return (mBluetoothAdapter != null) && (mBluetoothAdapter.isEnabled());
    }

    private IBeacon formatScanData(BluetoothDevice device, int rssi, byte[] scanData) {
        int startByte = 2;
        boolean patternFound = false;
        while (startByte <= 5) {
            if (((int) scanData[startByte + 2] & 0xff) == 0x02 &&
                    ((int) scanData[startByte + 3] & 0xff) == 0x15) {
                // yes!  This is an iBeacon
                patternFound = true;
                break;
            } else if (((int) scanData[startByte] & 0xff) == 0x2d &&
                    ((int) scanData[startByte + 1] & 0xff) == 0x24 &&
                    ((int) scanData[startByte + 2] & 0xff) == 0xbf &&
                    ((int) scanData[startByte + 3] & 0xff) == 0x16) {
                IBeacon iBeacon = new IBeacon();
                iBeacon.major = 0;
                iBeacon.minor = 0;
                iBeacon.proximityUuid = "00000000-0000-0000-0000-000000000000";
                iBeacon.txPower = -55;
                return iBeacon;
            } else if (((int) scanData[startByte] & 0xff) == 0xad &&
                    ((int) scanData[startByte + 1] & 0xff) == 0x77 &&
                    ((int) scanData[startByte + 2] & 0xff) == 0x00 &&
                    ((int) scanData[startByte + 3] & 0xff) == 0xc6) {

                IBeacon iBeacon = new IBeacon();
                iBeacon.major = 0;
                iBeacon.minor = 0;
                iBeacon.proximityUuid = "00000000-0000-0000-0000-000000000000";
                iBeacon.txPower = -55;
                return iBeacon;
            }
            startByte++;
        }


        if (patternFound == false) {
            // This is not an iBeacon
            return null;
        }

        IBeacon iBeacon = new IBeacon();

        iBeacon.major = (scanData[startByte + 20] & 0xff) * 0x100 + (scanData[startByte + 21] & 0xff);
        iBeacon.minor = (scanData[startByte + 22] & 0xff) * 0x100 + (scanData[startByte + 23] & 0xff);
        iBeacon.txPower = (int) scanData[startByte + 24]; // this one is signed
        iBeacon.rssi = rssi;
        iBeacon.distance = computeAccuracy(iBeacon.rssi, iBeacon.txPower);

        // AirLocate:
        // 02 01 1a 1a ff 4c 00 02 15  # Apple's fixed iBeacon advertising prefix
        // e2 c5 6d b5 df fb 48 d2 b0 60 d0 f5 a7 10 96 e0 # iBeacon profile uuid
        // 00 00 # major
        // 00 00 # minor
        // c5 # The 2's complement of the calibrated Tx Power

        // Estimote:
        // 02 01 1a 11 07 2d 24 bf 16
        // 394b31ba3f486415ab376e5c0f09457374696d6f7465426561636f6e00000000000000000000000000000000000000000000000000

        byte[] proximityUuidBytes = new byte[16];
        System.arraycopy(scanData, startByte + 4, proximityUuidBytes, 0, 16);
        String hexString = bytesToHexString(proximityUuidBytes);
        StringBuilder sb = new StringBuilder();
        sb.append(hexString.substring(0, 8));
        sb.append("-");
        sb.append(hexString.substring(8, 12));
        sb.append("-");
        sb.append(hexString.substring(12, 16));
        sb.append("-");
        sb.append(hexString.substring(16, 20));
        sb.append("-");
        sb.append(hexString.substring(20, 32));
        iBeacon.proximityUuid = sb.toString();

        if (device != null) {
            iBeacon.bluetoothAddress = device.getAddress();
            iBeacon.name = device.getName();
        }

        return iBeacon;
    }

    private String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    private double computeAccuracy(int rssi, int power) {
        if (rssi < 0 && power < 0) {
            double ratio = Double.parseDouble(String.valueOf(rssi)) / (double) power;
            double rssiCorrection = 0.96D + Math.pow((double) Math.abs(rssi), 3.0D) % 10.0D / 150.0D;
            if (ratio <= 1.0D) {
                return Math.pow(ratio, 9.98D) * rssiCorrection;
            } else {
                double distance = Math.max(0.0D, (0.103D + 0.89978D * Math.pow(ratio, 7.5D)) * rssiCorrection);
                return 0.0D / 0.0 == distance ? -1.0D : distance;
            }
        } else {
            return -1.0D;
        }
    }

    public static class IBeacon {
        public String name;
        public int major;
        public int minor;
        public String proximityUuid;
        public String bluetoothAddress;
        public int txPower;
        public int rssi;
        public double distance;
    }

    public interface OnIBeaconListener {
        void onFindIBeacon(IBeacon beacon);

        void onComplete(List<IBeacon> iBeacons);

        void onError(String error);
    }

    public String formatBeaconData(IBeacon iBeacon) {
        if (iBeacon == null) {
            return "";
        }

        Map<String, String> map = new HashMap<>();
        map.put("beaconname", iBeacon.name == null ? "" : iBeacon.name);
        map.put("beaconmac", iBeacon.bluetoothAddress == null ? "" : iBeacon.bluetoothAddress);
        map.put("beacondistance", iBeacon.distance + "");
        map.put("beaconid", iBeacon.proximityUuid == null ? "" : iBeacon.proximityUuid);
        map.put("beaconrssi", iBeacon.rssi + "");
        map.put("beaconpower", iBeacon.txPower + "");
        map.put("major", iBeacon.major + "");
        map.put("minor", iBeacon.minor + "");

        return new JSONObject(map).toString();
    }

    public String formatBeaconData(List<IBeacon> beacons) {
        if (beacons == null || beacons.size() < 1) {
            return "";
        }

        JSONArray array = new JSONArray();
        JSONObject obj;
        Map<String, String> map = new HashMap<>();

        String name;
        String mac;
        String uuid;
        int major;
        int minor;
        int rssi;
        int power;
        double distance;

        for (IBeacon item : beacons) {
            name = item.name;
            mac = item.bluetoothAddress;
            uuid = item.proximityUuid;
            major = item.major;
            minor = item.minor;
            rssi = item.rssi;
            power = item.txPower;
            distance = item.distance;

            map.put("beaconname", name == null ? "" : name);
            map.put("beaconmac", mac == null ? "" : mac);
            map.put("beacondistance", distance + "");
            map.put("beaconid", uuid == null ? "" : uuid);
            map.put("beaconrssi", rssi + "");
            map.put("beaconpower", power + "");
            map.put("major", major + "");
            map.put("minor", minor + "");

            obj = new JSONObject(map);
            array.put(obj);
        }

        return array.toString();
    }
}
