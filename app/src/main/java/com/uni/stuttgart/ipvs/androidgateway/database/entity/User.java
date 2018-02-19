package com.uni.stuttgart.ipvs.androidgateway.database.entity;

/**
 * Created by mdand on 2/18/2018.
 */

public class User {
    private String userName;
    private String userPass;
    private String userRole;

    public User() {}

    public User(String userName, String userPass, String userRole) {
        this.userName = userName;
        this.userPass = userPass;
        this.userRole = userRole;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPass() {
        return userPass;
    }

    public void setUserPass(String userPass) {
        this.userPass = userPass;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }
}
