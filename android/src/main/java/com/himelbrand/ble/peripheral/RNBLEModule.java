package com.himelbrand.ble.peripheral;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;


import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;




/**
 * {@link NativeModule} that allows JS to open the default browser
 * for an url.
 */
public class RNBLEModule extends ReactContextBaseJavaModule{

    ReactApplicationContext reactContext;
    HashMap<String, BluetoothGattService> servicesMap;
    HashSet<BluetoothDevice> mBluetoothDevices;
    BluetoothManager mBluetoothManager;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothGattServer mGattServer;
    BluetoothLeAdvertiser advertiser;
    AdvertiseCallback advertisingCallback;
    String name;
    boolean advertising;
    private Context context;

    public RNBLEModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.context = reactContext;
        this.servicesMap = new HashMap<String, BluetoothGattService>();
        this.advertising = false;
        this.name = "RN_BLE";
    }

    @Override
    public String getName() {
        return "BLEPeripheral";
    }

    @ReactMethod
    public void setName(String name) {
        this.name = name;
        Log.i("RNBLEModule", "name set to " + name);
    }

    @ReactMethod
    public void addService(String uuid, Boolean primary) {
        UUID SERVICE_UUID = UUID.fromString(uuid);
        int type = primary ? BluetoothGattService.SERVICE_TYPE_PRIMARY : BluetoothGattService.SERVICE_TYPE_SECONDARY;
        BluetoothGattService tempService = new BluetoothGattService(SERVICE_UUID, type);
        if(!this.servicesMap.containsKey(uuid))
            this.servicesMap.put(uuid, tempService);
    }

    @ReactMethod
    public void addCharacteristicToService(String serviceUUID, String uuid, Integer permissions, Integer properties) {
        UUID CHAR_UUID = UUID.fromString(uuid);
        Log.d("RNBLEModule", "Properties: " + String.valueOf(properties));
        Log.d("RNBLEModule", "Permissions: " + String.valueOf(permissions));

        // BluetoothGattCharacteristic tempChar = new BluetoothGattCharacteristic(
        //     CHAR_UUID,
        //     properties,
        //     permissions);
        BluetoothGattCharacteristic tempChar = new BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        this.servicesMap.get(serviceUUID).addCharacteristic(tempChar);
    }

    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.d("RNBLEModule", "onConnectionStateChange:" + String.valueOf(status));
            WritableMap params = Arguments.createMap();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    mBluetoothDevices.add(device);
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    mBluetoothDevices.remove(device);
                }
                params.putInt("status", newState);
                params.putString("device", device.toString());
                sendEvent("stateChanged", params);

            } else {
                mBluetoothDevices.remove(device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                        Log.d("RNBLEModule", "onCharacteristicReadRequest");

            if (offset != 0) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset,
                        /* value (optional) */ null);
                return;
            }
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, characteristic.getValue());
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
                                    Log.d("RNBLEModule", "onNotificationSent");

        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value);
            Log.d("RNBLEModule", "onCharacteristicWriteRequest:");

            characteristic.setValue(value);
            WritableMap map = Arguments.createMap();
            WritableArray data = Arguments.createArray();
            for (byte b : value) {
                            Log.d("RNBLEModule", "onCharacteristicWriteRequest:" + String.valueOf((int) b));

                data.pushInt((int) b);
            }
            Log.d("RNBLEModule", "onCharacteristicWriteRequestsss:" + characteristic.getUuid().toString());
            map.putString("characteristic", characteristic.getUuid().toString());
            map.putArray("data", data);
            map.putString("device", device.toString());
            // Log.d("RNBLEModule", "onCharacteristicWriteRequest1:" + data );
            Log.d("RNBLEModule", "done receiving" );
            sendEvent("onCharacteristicWriteRequest", map);
            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }
    };

    @ReactMethod
    public void start(final Promise promise){
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothAdapter.setName(this.name);
        // Ensures Bluetooth is available on the device and it is enabled. If not,
// displays a dialog requesting user permission to enable Bluetooth.

        mBluetoothDevices = new HashSet<>();
        mGattServer = mBluetoothManager.openGattServer(reactContext, mGattServerCallback);
        for (BluetoothGattService service : this.servicesMap.values()) {
            mGattServer.addService(service);
        }
        advertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();


        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder()
                .setIncludeDeviceName(true);
        for (BluetoothGattService service : this.servicesMap.values()) {
            dataBuilder.addServiceUuid(new ParcelUuid(service.getUuid()));
        }
        AdvertiseData data = dataBuilder.build();
        Log.i("RNBLEModule", data.toString());

        advertisingCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                advertising = true;
                promise.resolve("Success, Started Advertising");

            }

            @Override
            public void onStartFailure(int errorCode) {
                advertising = false;
                Log.e("RNBLEModule", "Advertising onStartFailure: " + errorCode);
                promise.reject("Advertising onStartFailure: " + errorCode);
                super.onStartFailure(errorCode);
            }
        };

        advertiser.startAdvertising(settings, data, advertisingCallback);

    }

    public void sendEvent(String eventName, WritableMap params) {
        Log.d("RNBLEModule", "eventName: " + eventName);
        getReactApplicationContext().getJSModule(RCTNativeAppEventEmitter.class).emit(eventName, params);

    }

    @ReactMethod
    public void stop(){
        if (mGattServer != null) {
            mGattServer.close();
        }
        if (mBluetoothAdapter !=null && mBluetoothAdapter.isEnabled() && advertiser != null) {
            // If stopAdvertising() gets called before close() a null
            // pointer exception is raised.
            advertiser.stopAdvertising(advertisingCallback);
        }
    }
    @ReactMethod
    public void sendNotificationToDevices(String serviceUUID,String charUUID,ReadableArray message) {
        byte[] decoded = new byte[message.size()];
        for (int i = 0; i < message.size(); i++) {
            decoded[i] = new Integer(message.getInt(i)).byteValue();
        }
        BluetoothGattCharacteristic characteristic = servicesMap.get(serviceUUID).getCharacteristic(UUID.fromString(charUUID));
        characteristic.setValue(decoded);
        boolean indicate = (characteristic.getProperties()
                & BluetoothGattCharacteristic.PROPERTY_INDICATE)
                == BluetoothGattCharacteristic.PROPERTY_INDICATE;
        for (BluetoothDevice device : mBluetoothDevices) {
            // true for indication (acknowledge) and false for notification (un-acknowledge).
            mGattServer.notifyCharacteristicChanged(device, characteristic, indicate);
        }
    }
    @ReactMethod
    public void isAdvertising(Promise promise){
        promise.resolve(this.advertising);
    }

}