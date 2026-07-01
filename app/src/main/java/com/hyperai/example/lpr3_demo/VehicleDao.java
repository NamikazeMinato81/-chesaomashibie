package com.hyperai.example.lpr3_demo;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 车辆数据访问对象 (DAO)
 */
@Dao
public interface VehicleDao {

    /** 查询所有车辆 */
    @Query("SELECT * FROM vehicle ORDER BY plate_number ASC")
    List<Vehicle> getAllVehicles();

    /** 根据车牌号查询单辆车 */
    @Query("SELECT * FROM vehicle WHERE plate_number = :plateNumber LIMIT 1")
    Vehicle getVehicleByPlate(String plateNumber);

    /** 查询是否为内部车（返回非空即存在） */
    @Query("SELECT * FROM vehicle WHERE plate_number = :plateNumber AND is_internal = 1 LIMIT 1")
    Vehicle getInternalVehicleByPlate(String plateNumber);

    /** 插入一辆车（若冲突则替换） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertVehicle(Vehicle vehicle);

    /** ===== 批量插入，冲突时忽略（用于 Excel 去重导入） ===== */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertVehiclesIgnore(List<Vehicle> vehicles);

    /** ===== 查询车牌号是否存在（用于去重计数） ===== */
    @Query("SELECT COUNT(*) FROM vehicle WHERE plate_number = :plateNumber")
    int countByPlate(String plateNumber);

    /** 更新一辆车 */
    @Update
    void updateVehicle(Vehicle vehicle);

    /** 删除一辆车 */
    @Delete
    void deleteVehicle(Vehicle vehicle);

    /** 根据车牌号删除 */
    @Query("DELETE FROM vehicle WHERE plate_number = :plateNumber")
    void deleteVehicleByPlate(String plateNumber);

    /** 删除所有车辆 */
    @Query("DELETE FROM vehicle")
    void deleteAllVehicles();
}
