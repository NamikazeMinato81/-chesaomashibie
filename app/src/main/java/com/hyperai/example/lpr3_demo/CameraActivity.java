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
 * 修改：集成 Room 数据库查询 + 单次阻断机制（弹窗期间暂停扫描）
 */
public class CameraActivity extends Activity {

    FrameLayout previewFl;
    CameraPreviews cameraPreview;
    TextView plateTv;
    ImageView image;

    /** 数据库访问对象 */
    private VehicleDao vehicleDao;

    /** 当前已处理、需抑制重复弹窗的车牌（同一车牌停留画面期间不再重复弹出） */
    private String suppressedPlate = "";
    /** 是否已有结果弹窗在显示（显示期间忽略后续识别事件，避免弹窗堆叠） */
    private boolean dialogShowing = false;
    /** 连续空帧计数，用于判断车牌是否已移出画面 */
    private int absentFrames = 0;
    /** 连续约 5 帧（~0.15s）无车牌即认为已离开，解除抑制 */
    private static final int ABSENT_THRESHOLD = 5;

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
        boolean hasPlate = (plates != null && plates.length > 0);

        // 没有识别到车牌：累计空帧，达到阈值则解除对上一车牌的抑制（视为已移出画面）
        if (!hasPlate) {
            absentFrames++;
            if (absentFrames >= ABSENT_THRESHOLD) {
                suppressedPlate = "";
            }
            return;
        }
        absentFrames = 0;

        // 更新底部识别文本（取所有非空车牌），并取首个非空车牌作为本次结果
        StringBuilder showText = new StringBuilder();
        String bestCode = null;
        for (Plate plate : plates) {
            String code = plate.getCode();
            if (code == null) code = "";
            String type = "未知车牌";
            int t = plate.getType();
            if (t != HyperLPR3.PLATE_TYPE_UNKNOWN
                    && t >= 0 && t < HyperLPR3.PLATE_TYPE_MAPS.length) {
                type = HyperLPR3.PLATE_TYPE_MAPS[t];
            }
            showText.append("[").append(type).append("]").append(code).append("\n");
            if (bestCode == null && !code.isEmpty()) {
                bestCode = code;
            }
        }
        plateTv.setText(showText.toString());

        if (bestCode == null) return;

        // 统一标准化，与导入存储保持一致，避免空格导致查不到
        String normalized = PlateUtils.normalizePlate(bestCode);
        if (normalized.isEmpty()) return;

        // 已有弹窗显示中，或同一车牌仍在画面内 -> 不重复弹窗
        if (dialogShowing || normalized.equals(suppressedPlate)) {
            return;
        }

        suppressedPlate = normalized;
        dialogShowing = true;
        queryVehicleAndShowDialog(normalized);
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
                            dialogShowing = false;
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
                            dialogShowing = false;
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
