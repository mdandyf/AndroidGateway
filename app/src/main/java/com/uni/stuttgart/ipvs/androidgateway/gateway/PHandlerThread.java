package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.bluetooth.BluetoothGatt;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.Parcelable;

public final class PHandlerThread implements Parcelable {

    public HandlerThread callback;

    public PHandlerThread() {}

    private PHandlerThread(Parcel in) { readToParcel(in); }

    public static final Parcelable.Creator<PHandlerThread> CREATOR = new Parcelable.Creator<PHandlerThread>() {
        @Override
        public PHandlerThread createFromParcel(Parcel in) {
            return new PHandlerThread(in);
        }

        @Override
        public PHandlerThread[] newArray(int size) {
            return new PHandlerThread[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(callback);
    }

    private void readToParcel(Parcel in) { callback = (HandlerThread) in.readValue(Handler.Callback.class.getClassLoader()); }

    public HandlerThread getHandlerThread() { return callback; }

    public void setHandlerThread(HandlerThread callback) { this.callback = callback; }
}
