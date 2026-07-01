package com.hyperai.example.lpr3_demo;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 车辆白名单管理界面
 * 支持增删改查 + Excel 批量导入
 */
public class VehicleManageActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private EditText etSearch;
    private Button btnAdd;
    private Button btnImportExcel;

    private VehicleDao vehicleDao;
    private VehicleAdapter adapter;
    private List<Vehicle> vehicleList = new ArrayList<>();
    private List<Vehicle> filteredList = new ArrayList<>();

    /** 文件选择请求码 */
    private static final int REQUEST_CODE_PICK_EXCEL = 100;

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
        btnImportExcel = findViewById(R.id.btn_import_excel);

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

        // ===== 导入 Excel 按钮 =====
        btnImportExcel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 打开文件选择器，选择 Excel 文件
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                // 也支持 .xls 格式
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel"
                });
                startActivityForResult(intent, REQUEST_CODE_PICK_EXCEL);
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

    // ========================================================================
    // ===== 文件选择回调 + Excel 批量导入功能 =====
    // ========================================================================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_EXCEL && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importExcelFile(uri);
            }
        }
    }

    /**
     * 解析并导入 Excel 文件
     * Excel 格式要求：
     *   第1行：表头（A: 车牌号, B: 车主姓名, C: 是否内部车, D: 备注）
     *   第2行起：数据行
     */
    private void importExcelFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show();
                return;
            }

            // 使用 POI 解析 Excel
            Workbook workbook = WorkbookFactory.create(inputStream);
            Sheet sheet = workbook.getSheetAt(0);

            if (sheet.getPhysicalNumberOfRows() <= 1) {
                Toast.makeText(this, "Excel 文件中没有数据行（至少需要表头+1行数据）", Toast.LENGTH_SHORT).show();
                workbook.close();
                inputStream.close();
                return;
            }

            // 解析数据行（从第2行开始，第1行是表头）
            List<Vehicle> vehiclesToInsert = new ArrayList<>();
            int skipRows = 0;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // 读取各列
                String plateNumber = getCellStringValue(row.getCell(0));
                String ownerName = getCellStringValue(row.getCell(1));
                boolean isInternal = getCellBooleanValue(row.getCell(2));
                String remark = getCellStringValue(row.getCell(3));

                // 车牌号不能为空
                if (plateNumber == null || plateNumber.trim().isEmpty()) {
                    skipRows++;
                    continue;
                }

                plateNumber = plateNumber.trim();

                // 去重检查：如果数据库中已存在该车牌，跳过
                int existingCount = vehicleDao.countByPlate(plateNumber);
                if (existingCount > 0) {
                    skipRows++;
                    continue;
                }

                vehiclesToInsert.add(new Vehicle(plateNumber, ownerName, isInternal, remark));
            }

            workbook.close();
            inputStream.close();

            // 批量插入
            if (!vehiclesToInsert.isEmpty()) {
                vehicleDao.insertVehiclesIgnore(vehiclesToInsert);
            }

            final int successCount = vehiclesToInsert.size();
            final int skippedCount = skipRows;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String message = "导入完成！\n"
                            + "成功导入：" + successCount + " 条\n"
                            + "跳过（已存在/无效）：" + skippedCount + " 条";
                    Toast.makeText(VehicleManageActivity.this, message, Toast.LENGTH_LONG).show();
                    loadVehicles(); // 刷新列表
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(VehicleManageActivity.this,
                            "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /**
     * 获取单元格的字符串值
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // 如果是数字（如车牌号纯数字），转成字符串
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    return String.valueOf((long) val);
                }
                return String.valueOf(val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }

    /**
     * 获取单元格的布尔值
     * 支持：true/false, 是/否, 1/0, 内部/外部
     */
    private boolean getCellBooleanValue(Cell cell) {
        if (cell == null) return false;
        switch (cell.getCellType()) {
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case NUMERIC:
                return cell.getNumericCellValue() == 1;
            case STRING:
                String val = cell.getStringCellValue().trim();
                return val.equals("是") || val.equals("内部") || val.equals("1")
                        || val.equalsIgnoreCase("true") || val.equalsIgnoreCase("yes");
            default:
                return false;
        }
    }
}
