package com.hyperai.example.lpr3_demo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.hyperai.hyperlpr3.HyperLPR3;
import com.hyperai.hyperlpr3.bean.Plate;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;


/**
 * @author by hs-johnny
 * Created on 2019/6/17
 *
 * 修改：集成 Room 数据库查询，识别车牌后判断是否为内部车并弹窗提示
 */
public class CameraActivity extends Activity {

    FrameLayout previewFl;
    CameraPreviews cameraPreview;
    TextView plateTv;
    ImageView image;

    /** 数据库访问对象 */
    private VehicleDao vehicleDao;

    /** 上次识别的车牌，用于防重复弹窗 */
    private String lastRecognizedPlate = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera);

        // 初始化 Room 数据库 DAO
        vehicleDao = AppDatabase.getInstance(this).vehicleDao();
    }

    private void initCamera() {
        previewFl = findViewById(R.id.preview_fl);
        plateTv = findViewById(R.id.plate_tv);
        image = findViewById(R.id.image);
        cameraPreview = new CameraPreviews(this);
        previewFl.addView(cameraPreview);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraPreview == null) {
            initCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraPreview = null;
    }

    private void stopPreview() {
        previewFl.removeAllViews();
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(Plate[] plates) {

        String showText = "";
        for (Plate plate: plates) {
            String type = "未知车牌";
            if (plate.getType() != HyperLPR3.PLATE_TYPE_UNKNOWN) {
                type = HyperLPR3.PLATE_TYPE_MAPS[plate.getType()];
            }
            String pStr = "[" + type + "]" + plate.getCode() + "\n";
            showText += pStr;
            plateTv.setText(showText);

            // ===== 新增：识别到车牌后查询数据库 =====
            String plateCode = plate.getCode();
            if (plateCode != null && !plateCode.isEmpty()) {
                // 避免对同一车牌重复弹窗
                if (!plateCode.equals(lastRecognizedPlate)) {
                    lastRecognizedPlate = plateCode;
                    queryVehicleAndShowDialog(plateCode);
                }
            }
        }
    }

    /**
     * 查询数据库并根据结果弹窗
     * @param plateCode 识别的车牌号
     */
    private void queryVehicleAndShowDialog(String plateCode) {
        // 在子线程查询数据库
        new Thread(new Runnable() {
            @Override
            public void run() {
                Vehicle vehicle = vehicleDao.getVehicleByPlate(plateCode);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showVehicleDialog(plateCode, vehicle);
                    }
                });
            }
        }).start();
    }

    /**
     * 显示车辆识别结果弹窗
     * @param plateCode 车牌号
     * @param vehicle   数据库查询结果（null 表示未在白名单中）
     */
    private void showVehicleDialog(String plateCode, Vehicle vehicle) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        if (vehicle != null && vehicle.isInternal()) {
            // ===== 内部车：绿色放行 =====
            String message = "车牌号：" + plateCode + "\n"
                    + "车主：" + vehicle.getOwnerName() + "\n"
                    + "类型：内部车辆\n"
                    + "备注：" + (vehicle.getRemark() != null ? vehicle.getRemark() : "无");
            builder.setTitle("✅ 放行")
                    .setMessage(message)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            // 创建并设置背景色为绿色
            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                    dialog.getWindow().getDecorView().setBackgroundColor(Color.parseColor("#CC4CAF50"));
                }
            });
            dialog.show();
        } else {
            // ===== 外来车辆：红色警告 =====
            String message;
            if (vehicle != null && !vehicle.isInternal()) {
                message = "车牌号：" + plateCode + "\n"
                        + "车主：" + vehicle.getOwnerName() + "\n"
                        + "类型：已登记外来车辆\n"
                        + "备注：" + (vehicle.getRemark() != null ? vehicle.getRemark() : "无");
            } else {
                message = "车牌号：" + plateCode + "\n"
                        + "该车辆未在白名单中登记！";
            }
            builder.setTitle("⚠️ 外来车辆")
                    .setMessage(message)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            // 创建并设置背景色为红色
            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                    dialog.getWindow().getDecorView().setBackgroundColor(Color.parseColor("#CCF44336"));
                }
            });
            dialog.show();
        }
    }

}
