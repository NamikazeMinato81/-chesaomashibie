package com.hyperai.example.lpr3_demo;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 车辆白名单管理界面
 * 支持增删改查操作
 */
public class VehicleManageActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private EditText etSearch;
    private Button btnAdd;

    private VehicleDao vehicleDao;
    private VehicleAdapter adapter;
    private List<Vehicle> vehicleList = new ArrayList<>();
    private List<Vehicle> filteredList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_manage);

        // 初始化数据库
        vehicleDao = AppDatabase.getInstance(this).vehicleDao();

        // 初始化视图
        recyclerView = findViewById(R.id.recycler_view);
        tvEmpty = findViewById(R.id.tv_empty);
        etSearch = findViewById(R.id.et_search);
        btnAdd = findViewById(R.id.btn_add);

        // 设置 RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VehicleAdapter(this, filteredList);
        recyclerView.setAdapter(adapter);

        // 加载数据
        loadVehicles();

        // 添加按钮
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVehicleEditorDialog(null);
            }
        });

        // 编辑按钮
        adapter.setOnEditClickListener(new VehicleAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Vehicle vehicle, int position) {
                showVehicleEditorDialog(vehicle);
            }
        });

        // 删除按钮
        adapter.setOnDeleteClickListener(new VehicleAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Vehicle vehicle, int position) {
                showDeleteConfirmDialog(vehicle);
            }
        });

        // 搜索功能
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterVehicles(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * 从数据库加载所有车辆
     */
    private void loadVehicles() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                vehicleList = vehicleDao.getAllVehicles();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        filterVehicles(etSearch.getText().toString());
                    }
                });
            }
        }).start();
    }

    /**
     * 根据搜索关键词过滤
     */
    private void filterVehicles(String keyword) {
        filteredList.clear();
        if (keyword == null || keyword.isEmpty()) {
            filteredList.addAll(vehicleList);
        } else {
            for (Vehicle v : vehicleList) {
                if (v.getPlateNumber() != null && v.getPlateNumber().contains(keyword)) {
                    filteredList.add(v);
                }
            }
        }
        adapter.updateData(filteredList);

        // 显示空状态
        if (filteredList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 显示添加/编辑车辆对话框
     * @param existingVehicle 已有车辆（编辑模式），null 表示新增
     */
    private void showVehicleEditorDialog(Vehicle existingVehicle) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_vehicle_editor, null);
        builder.setView(view);

        EditText etPlateNumber = view.findViewById(R.id.et_plate_number);
        EditText etOwnerName = view.findViewById(R.id.et_owner_name);
        CheckBox cbIsInternal = view.findViewById(R.id.cb_is_internal);
        EditText etRemark = view.findViewById(R.id.et_remark);

        boolean isEditMode = (existingVehicle != null);
        if (isEditMode) {
            etPlateNumber.setText(existingVehicle.getPlateNumber());
            etPlateNumber.setEnabled(false); // 编辑时不允许修改车牌号（主键）
            etOwnerName.setText(existingVehicle.getOwnerName());
            cbIsInternal.setChecked(existingVehicle.isInternal());
            etRemark.setText(existingVehicle.getRemark());
        }

        builder.setTitle(isEditMode ? "编辑车辆" : "添加车辆")
                .setPositiveButton("保存", null) // 先 null，后面重写
                .setNegativeButton("取消", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // 重写 PositiveButton 以支持输入校验
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String plateNumber = etPlateNumber.getText().toString().trim();
                String ownerName = etOwnerName.getText().toString().trim();
                boolean isInternal = cbIsInternal.isChecked();
                String remark = etRemark.getText().toString().trim();

                // 校验车牌号不能为空
                if (plateNumber.isEmpty()) {
                    Toast.makeText(VehicleManageActivity.this, "请输入车牌号", Toast.LENGTH_SHORT).show();
                    return;
                }

                Vehicle vehicle = new Vehicle(plateNumber, ownerName, isInternal, remark);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        vehicleDao.insertVehicle(vehicle);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(VehicleManageActivity.this,
                                        isEditMode ? "修改成功" : "添加成功", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                loadVehicles(); // 刷新列表
                            }
                        });
                    }
                }).start();
            }
        });
    }

    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmDialog(Vehicle vehicle) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除车牌号为「" + vehicle.getPlateNumber() + "」的车辆吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    new Thread(() -> {
                        vehicleDao.deleteVehicle(vehicle);
                        runOnUiThread(() -> {
                            Toast.makeText(VehicleManageActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                            loadVehicles(); // 刷新列表
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
