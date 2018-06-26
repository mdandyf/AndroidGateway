package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.os.Parcel;
import android.os.Parcelable;

public final class PManufacturer implements Parcelable {

    public String id;
    public String name;
    public String service;

    public PManufacturer() {}


    protected PManufacturer(Parcel in) {
        id = in.readString();
        name = in.readString();
        service = in.readString();
    }


    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getService() {
        return service;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setService(String service) {
        this.service = service;
    }

    public static final Creator<PManufacturer> CREATOR = new Creator<PManufacturer>() {
        @Override
        public PManufacturer createFromParcel(Parcel in) {
            return new PManufacturer(in);
        }

        @Override
        public PManufacturer[] newArray(int size) {
            return new PManufacturer[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(id);
        parcel.writeString(name);
        parcel.writeString(service);
    }
}
