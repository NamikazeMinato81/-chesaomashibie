package com.hyperai.example.lpr3_demo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.hyperai.hyperlpr3.HyperLPR3;
import com.hyperai.hyperlpr3.bean.HyperLPRParameter;
import com.hyperai.hyperlpr3.bean.Plate;
import com.yuyh.library.imgsel.ISNav;
import com.yuyh.library.imgsel.common.ImageLoader;
import com.yuyh.library.imgsel.config.ISListConfig;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Button cameraBtn;
    private Button albumBtn;
    private Button manageBtn;
    private Context mCtx;
    private static final int REQUEST_LIST_CODE = 0;
    private static final int REQUEST_CAMERA_CODE = 1;

    private final String TAG = "HyperLPR-App";

    private ImageView imageView;

    private TextView mResult;

    /** 数据库访问对象 */
    private VehicleDao vehicleDao;


    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.CAMERA"};

    public static void verifyStoragePermissions(Activity activity) {

        try {
            //检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);

            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCtx = this;
        cameraBtn = findViewById(R.id.cameraBtn);
        albumBtn = findViewById(R.id.albumBtn);
        manageBtn = findViewById(R.id.manageBtn);
        imageView = findViewById(R.id.imageView);
        mResult = findViewById(R.id.mResult);

        // 初始化 Room 数据库 DAO
        vehicleDao = AppDatabase.getInstance(this).vehicleDao();

        verifyStoragePermissions(this);

        // 车牌识别算法配置参数
        HyperLPRParameter parameter = new HyperLPRParameter()
                .setDetLevel(HyperLPR3.DETECT_LEVEL_LOW)
                .setMaxNum(1)
                .setRecConfidenceThreshold(0.85f);
        // 初始化(仅执行一次生效)
        HyperLPR3.getInstance().init(this, parameter);

        ISNav.getInstance().init(new ImageLoader() {
            @Override
            public void displayImage(Context context, String path, ImageView imageView) {
                Glide.with(context).load(path).into(imageView);
            }
        });

        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                MainActivity.this.startActivity(intent);
            }
        });

        albumBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // 自由配置选项
                ISListConfig config = new ISListConfig.Builder()
                        // 是否多选, 默认true
                        .multiSelect(false)
                        // 是否记住上次选中记录, 仅当multiSelect为true的时候配置，默认为true
                        .rememberSelected(false)
                        // "确定"按钮背景色
                        .btnBgColor(Color.GRAY)
                        // "确定"按钮文字颜色
                        .btnTextColor(Color.BLUE)
                        // 使用沉浸式状态栏
                        .statusBarColor(Color.parseColor("#3F51B5"))
                        // 返回图标ResId
                        .backResId(androidx.appcompat.R.drawable.abc_cab_background_top_mtrl_alpha)
                        // 标题
                        .title("图片")
                        // 标题文字颜色
                        .titleColor(Color.WHITE)
                        // TitleBar背景色
                        .titleBgColor(Color.parseColor("#3F51B5"))
                        // 裁剪大小。needCrop为true的时候配置
                        .cropSize(1, 1, 200, 200)
                        .needCrop(false)
                        // 第一个是否显示相机，默认true
                        .needCamera(false)
                        // 最大选择图片数量，默认9
                        .maxNum(1)
                        .build();

                // 跳转到图片选择器
                ISNav.getInstance().toListActivity(mCtx, config, REQUEST_LIST_CODE);
            }
        });

        // ===== 白名单管理按钮 =====
        manageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, VehicleManageActivity.class);
                MainActivity.this.startActivity(intent);
            }
        });

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Bitmap bitmap = null;
        String showText = "";
        // 图片选择结果回调
        if (requestCode == REQUEST_LIST_CODE && resultCode == RESULT_OK && data != null) {
            List<String> pathList = data.getStringArrayListExtra("result");
            Log.i(TAG, pathList.get(0));
            bitmap = BitmapFactory.decodeFile(pathList.get(0));
        }  else if (requestCode == REQUEST_CAMERA_CODE && resultCode == RESULT_OK && data != null) {
            String path = data.getStringExtra("result");
            Log.i(TAG, path);
            bitmap = BitmapFactory.decodeFile(path);
        }
        if (bitmap != null) {

            imageView.setImageBitmap(bitmap);
            // 调用车牌识别
            Plate[] plates =  HyperLPR3.getInstance().plateRecognition(bitmap, HyperLPR3.CAMERA_ROTATION_0, HyperLPR3.STREAM_BGRA);

            // ===== 判断是否识别到车牌 =====
            if (plates == null || plates.length == 0) {
                // 未检测到车牌
                Toast.makeText(MainActivity.this,
                        "未检测到有效车牌，请重新选择清晰的照片", Toast.LENGTH_LONG).show();
                mResult.setText("未识别到车牌");
                return;
            }

            for (Plate plate: plates) {
                String type = "未知车牌";
                if (plate.getType() != HyperLPR3.PLATE_TYPE_UNKNOWN) {
                    type = HyperLPR3.PLATE_TYPE_MAPS[plate.getType()];
                }
                String pStr = "[" + type + "]" + plate.getCode() + "\n";
                showText += pStr;
                mResult.setText(showText);

                // ===== 相册识别结果也走数据库白名单比对 =====
                String plateCode = plate.getCode();
                if (plateCode != null && !plateCode.isEmpty()) {
                    queryVehicleAndShowDialog(plateCode);
                }
            }
        }
    }

    /**
     * ===== 查询数据库并根据结果弹窗（与 CameraActivity 逻辑一致） =====
     * @param plateCode 识别的车牌号
     */
    private void queryVehicleAndShowDialog(String plateCode) {
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
     */
    private void showVehicleDialog(String plateCode, Vehicle vehicle) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        if (vehicle != null && vehicle.isInternal()) {
            // 内部车：绿色放行
            String message = "车牌号：" + plateCode + "\n"
                    + "车主：" + vehicle.getOwnerName() + "\n"
                    + "类型：内部车辆\n"
                    + "备注：" + (vehicle.getRemark() != null ? vehicle.getRemark() : "无");
            builder.setTitle("✅ 放行")
                    .setMessage(message)
                    .setPositiveButton("确定", null);
            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                    dialog.getWindow().getDecorView().setBackgroundColor(Color.parseColor("#CC4CAF50"));
                }
            });
            dialog.show();
        } else {
            // 外来车辆：红色警告
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
                    .setPositiveButton("确定", null);
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
