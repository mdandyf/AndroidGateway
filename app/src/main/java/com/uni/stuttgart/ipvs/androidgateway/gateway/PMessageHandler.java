package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;

public final class PMessageHandler implements Parcelable {

    public Handler handlerMessage;

    public PMessageHandler() {}

    private PMessageHandler(Parcel in) { readToParcel(in); }

    public static final Parcelable.Creator<PMessageHandler> CREATOR = new Parcelable.Creator<PMessageHandler>() {
        @Override
        public PMessageHandler createFromParcel(Parcel in) {
            return new PMessageHandler(in);
        }

        @Override
        public PMessageHandler[] newArray(int size) {
            return new PMessageHandler[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(handlerMessage);
    }

    private void readToParcel(Parcel in) { handlerMessage = (Handler) in.readValue(Handler.class.getClassLoader()); }

    public Handler getHandlerMessage() { return handlerMessage; }

    public void setHandlerMessage(Handler handler) { this.handlerMessage = handler; }

}
