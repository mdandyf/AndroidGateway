package com.uni.stuttgart.ipvs.androidgateway.gateway;

//import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGatt;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

public final class PBluetoothGatt implements Parcelable {

    public BluetoothGatt gatt;

    public PBluetoothGatt() {}

    private PBluetoothGatt(Parcel in) { readToParcel(in); }

    public static final Parcelable.Creator<PBluetoothGatt> CREATOR = new Parcelable.Creator<PBluetoothGatt>() {
        @Override
        public PBluetoothGatt createFromParcel(Parcel in) {
            return new PBluetoothGatt(in);
        }

        @Override
        public PBluetoothGatt[] newArray(int size) {
            return new PBluetoothGatt[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(gatt);
    }

    private void readToParcel(Parcel in) { gatt = (BluetoothGatt) in.readValue(BluetoothGatt.class.getClassLoader()); }

    public BluetoothGatt getGatt() { return gatt; }

    public void setGatt(BluetoothGatt gatt) { this.gatt = gatt; }
}
