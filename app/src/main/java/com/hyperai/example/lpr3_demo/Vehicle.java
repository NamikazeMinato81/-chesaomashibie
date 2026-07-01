package com.hyperai.example.lpr3_demo;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 车辆白名单实体类
 * 对应 Vehicle 表
 */
@Entity(tableName = "vehicle")
public class Vehicle {

    /** 车牌号（主键） */
    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "plate_number")
    private String plateNumber;

    /** 车主姓名 */
    @ColumnInfo(name = "owner_name")
    private String ownerName;

    /** 是否为内部车 */
    @ColumnInfo(name = "is_internal")
    private boolean isInternal;

    /** 备注 */
    @ColumnInfo(name = "remark")
    private String remark;

    public Vehicle() {
    }

    @Ignore
    public Vehicle(String plateNumber, String ownerName, boolean isInternal, String remark) {
        this.plateNumber = plateNumber;
        this.ownerName = ownerName;
        this.isInternal = isInternal;
        this.remark = remark;
    }

    // ===== Getters & Setters =====

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public boolean isInternal() {
        return isInternal;
    }

    public void setInternal(boolean internal) {
        isInternal = internal;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "plateNumber='" + plateNumber + '\'' +
                ", ownerName='" + ownerName + '\'' +
                ", isInternal=" + isInternal +
                ", remark='" + remark + '\'' +
                '}';
    }
}
