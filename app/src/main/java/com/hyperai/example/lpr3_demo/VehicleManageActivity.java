package com.hyperai.example.lpr3_demo;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;

/**
 * 车辆白名单管理界面
 * 支持增删改查 + Excel 批量导入（支持 .xls 和 .xlsx 双格式）
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
    private static final String TAG = "VehicleManage";

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

        // ===== 导入 Excel 按钮（支持 .xls 和 .xlsx 双格式） =====
        btnImportExcel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                String[] mimeTypes = {
                    "application/vnd.ms-excel",                           // .xls
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" // .xlsx
                };
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
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
                .setPositiveButton("保存", null)
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
                                loadVehicles();
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
                            loadVehicles();
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ========================================================================
    // ===== 文件选择回调 + 双格式 Excel 导入（.xls / .xlsx） =====
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
     * 通过 ContentResolver 获取文件名的后缀
     */
    private String getFileExtension(Uri uri) {
        String extension = "";
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex >= 0) {
                String fileName = cursor.getString(nameIndex);
                if (fileName != null) {
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex >= 0) {
                        extension = fileName.substring(dotIndex).toLowerCase();
                    }
                }
            }
            cursor.close();
        }
        return extension;
    }

    /**
     * 统一的 Excel 导入入口
     * 在子线程中完成：打开流 → 解析 → 去重写入 → 关闭流
     * 确保 finally 中关闭流，杜绝 Stream closed 错误
     *
     * 关键设计：
     * - 先通过 getFileExtension() 获取后缀（不消耗 InputStream）
     * - 再通过 openInputStream() 创建全新 InputStream
     * - .xls 分支：jxl 直接读取原始 InputStream
     * - .xlsx 分支：先将整个 .xlsx 读到 ByteArrayOutputStream，
     *   再从 ByteArrayInputStream 创建 ZipInputStream，
     *   这样 ZipInputStream.close() 不会影响原始 InputStream
     */
    private void importExcelFile(Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream inputStream = null;
                try {
                    // 获取文件后缀，判断格式（不消耗 InputStream，仅查询 ContentResolver 元数据）
                    final String ext = getFileExtension(uri);
                    Log.d(TAG, "导入文件后缀: " + ext);

                    // 通过 ContentResolver 打开输入流（在子线程中打开，生命周期由子线程管理）
                    inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream == null) {
                        runOnUiThread(() -> Toast.makeText(VehicleManageActivity.this,
                                "无法读取文件", Toast.LENGTH_SHORT).show());
                        return;
                    }

                    // 解析结果
                    List<Vehicle> vehiclesToInsert;

                    if (".xls".equals(ext)) {
                        // .xls 格式 → 使用 jxl 直接解析原始 InputStream
                        Log.d(TAG, "使用 jxl 解析 .xls 文件");
                        vehiclesToInsert = parseXls(inputStream);
                    } else {
                        // .xlsx 格式 → 先将整个文件读到内存，再从 ByteArrayInputStream 创建 ZipInputStream
                        // 这样 ZipInputStream.close() 不会关闭原始 inputStream
                        Log.d(TAG, "使用原生 Zip+XML 解析 .xlsx 文件");
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = inputStream.read(buffer)) != -1) {
                            baos.write(buffer, 0, len);
                        }
                        baos.close();
                        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                        vehiclesToInsert = parseXlsx(bais);
                    }

                    // 去重检查并批量插入
                    int successCount = 0;
                    int skipped = 0;
                    if (vehiclesToInsert != null) {
                        List<Vehicle> finalList = new ArrayList<>();
                        for (Vehicle v : vehiclesToInsert) {
                            if (v.getPlateNumber() == null || v.getPlateNumber().trim().isEmpty()) {
                                skipped++;
                                continue;
                            }
                            int existingCount = vehicleDao.countByPlate(v.getPlateNumber());
                            if (existingCount > 0) {
                                skipped++;
                                continue;
                            }
                            finalList.add(v);
                        }
                        if (!finalList.isEmpty()) {
                            vehicleDao.insertVehiclesIgnore(finalList);
                            successCount = finalList.size();
                        }
                    }

                    final int finalSuccess = successCount;
                    final int finalSkipped = skipped;

                    // 主线程弹出 Toast 提示
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String message = "导入完成！\n"
                                    + "成功导入：" + finalSuccess + " 条\n"
                                    + "跳过（已存在/无效）：" + finalSkipped + " 条";
                            Toast.makeText(VehicleManageActivity.this, message, Toast.LENGTH_LONG).show();
                            loadVehicles();
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Excel 导入失败", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(VehicleManageActivity.this,
                                    "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    // ★ 绝对安全：只有在子线程所有操作完成后，才在 finally 中关闭流
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                            Log.d(TAG, "输入流已安全关闭");
                        } catch (Exception e) {
                            Log.e(TAG, "关闭输入流失败", e);
                        }
                    }
                }
            }
        }).start();
    }

    // ========================================================================
    // ===== .xls 解析（使用 jxl 轻量库） =====
    // ========================================================================

    /**
     * 使用 jxl 解析 .xls 文件
     * Excel 格式：A列=车牌号, B列=车主姓名, C列=是否内部车, D列=备注
     */
    private List<Vehicle> parseXls(InputStream inputStream) throws Exception {
        List<Vehicle> vehicles = new ArrayList<>();
        Workbook workbook = Workbook.getWorkbook(inputStream);
        Sheet sheet = workbook.getSheet(0);

        int rows = sheet.getRows();
        // 从第2行开始（第1行是表头）
        for (int i = 1; i < rows; i++) {
            try {
                Cell[] row = sheet.getRow(i);
                if (row.length < 1) continue;

                String plateNumber = getCellTrimmed(row, 0);
                String ownerName = getCellTrimmed(row, 1);
                String internalStr = getCellTrimmed(row, 2);
                String remark = getCellTrimmed(row, 3);

                if (plateNumber.isEmpty()) continue;

                boolean isInternal = false;
                if (!internalStr.isEmpty()) {
                    isInternal = internalStr.equals("是") || internalStr.equals("内部")
                            || internalStr.equals("1") || internalStr.equalsIgnoreCase("true")
                            || internalStr.equalsIgnoreCase("yes");
                }

                vehicles.add(new Vehicle(plateNumber, ownerName, isInternal, remark));

            } catch (Exception e) {
                Log.e(TAG, "解析 .xls 第 " + (i + 1) + " 行失败: " + e.getMessage(), e);
            }
        }

        workbook.close();
        return vehicles;
    }

    /**
     * 统一清理单元格字符串：去除首尾空格 + 移除所有换行符
     * 确保存入数据库的车牌号是绝对干净的
     */
    private String cleanCell(String raw) {
        if (raw == null) return "";
        return raw.trim().replace("\n", "").replace("\r", "");
    }

    private String getCellTrimmed(Cell[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) return "";
        String val = row[index].getContents();
        return cleanCell(val);
    }

    // ========================================================================
    // ===== .xlsx 解析（使用 Android 原生 ZipInputStream + XML 解析器） =====
    // ========================================================================

    /**
     * 使用 Android 原生 ZipInputStream + XML 解析器手动解析 .xlsx 文件
     * .xlsx 本质是 ZIP 包，包含 xl/sharedStrings.xml（字符串表）和 xl/worksheets/sheet1.xml（数据）
     */
    private List<Vehicle> parseXlsx(InputStream inputStream) throws Exception {
        List<String> sharedStrings = new ArrayList<>();
        List<String[]> sheetData = new ArrayList<>();

        ZipInputStream zis = new ZipInputStream(inputStream);
        ZipEntry entry;

        while ((entry = zis.getNextEntry()) != null) {
            String entryName = entry.getName();

            if ("xl/sharedStrings.xml".equals(entryName)) {
                sharedStrings = parseSharedStrings(zis);
            } else if ("xl/worksheets/sheet1.xml".equals(entryName)) {
                sheetData = parseSheetData(zis);
            }

            zis.closeEntry();
        }
        zis.close();

        // 解析数据行（第1行是表头，从第2行开始）
        List<Vehicle> vehicles = new ArrayList<>();
        for (int i = 1; i < sheetData.size(); i++) {
            try {
                String[] row = sheetData.get(i);
                if (row.length < 1) continue;

                String plateNumber = cleanCell(getCellValue(row, 0, sharedStrings));
                String ownerName = cleanCell(getCellValue(row, 1, sharedStrings));
                String internalStr = cleanCell(getCellValue(row, 2, sharedStrings));
                String remark = cleanCell(getCellValue(row, 3, sharedStrings));

                if (plateNumber.isEmpty()) continue;

                boolean isInternal = false;
                if (!internalStr.isEmpty()) {
                    isInternal = internalStr.equals("是") || internalStr.equals("内部")
                            || internalStr.equals("1") || internalStr.equalsIgnoreCase("true")
                            || internalStr.equalsIgnoreCase("yes");
                }

                vehicles.add(new Vehicle(plateNumber, ownerName, isInternal, remark));

            } catch (Exception e) {
                Log.e(TAG, "解析 .xlsx 第 " + (i + 1) + " 行失败: " + e.getMessage(), e);
            }
        }

        return vehicles;
    }

    private List<String> parseSharedStrings(InputStream inputStream) throws Exception {
        List<String> strings = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(inputStream);
        NodeList siNodes = doc.getElementsByTagName("si");
        for (int i = 0; i < siNodes.getLength(); i++) {
            Element si = (Element) siNodes.item(i);
            NodeList tNodes = si.getElementsByTagName("t");
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < tNodes.getLength(); j++) {
                sb.append(tNodes.item(j).getTextContent());
            }
            strings.add(sb.toString());
        }
        return strings;
    }

    private List<String[]> parseSheetData(InputStream inputStream) throws Exception {
        List<String[]> rows = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(inputStream);
        NodeList rowNodes = doc.getElementsByTagName("row");
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element rowElement = (Element) rowNodes.item(i);
            NodeList cellNodes = rowElement.getElementsByTagName("c");
            int maxCol = 0;
            for (int j = 0; j < cellNodes.getLength(); j++) {
                Element cell = (Element) cellNodes.item(j);
                String ref = cell.getAttribute("r");
                int colIndex = columnRefToIndex(ref);
                if (colIndex > maxCol) maxCol = colIndex;
            }
            String[] rowData = new String[maxCol + 1];
            for (int j = 0; j < cellNodes.getLength(); j++) {
                Element cell = (Element) cellNodes.item(j);
                String ref = cell.getAttribute("r");
                int colIndex = columnRefToIndex(ref);
                String type = cell.getAttribute("t");
                String value = "";
                NodeList vNodes = cell.getElementsByTagName("v");
                if (vNodes.getLength() > 0) {
                    value = vNodes.item(0).getTextContent();
                }
                if ("s".equals(type)) {
                    rowData[colIndex] = "___SI___" + value;
                } else {
                    rowData[colIndex] = value;
                }
            }
            rows.add(rowData);
        }
        return rows;
    }

    private int columnRefToIndex(String ref) {
        if (ref == null || ref.isEmpty()) return 0;
        StringBuilder colLetters = new StringBuilder();
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (Character.isLetter(c)) {
                colLetters.append(c);
            } else {
                break;
            }
        }
        String col = colLetters.toString().toUpperCase();
        int index = 0;
        for (int i = 0; i < col.length(); i++) {
            index = index * 26 + (col.charAt(i) - 'A' + 1);
        }
        return index - 1;
    }

    private String getCellValue(String[] rowData, int colIndex, List<String> sharedStrings) {
        if (rowData == null || colIndex >= rowData.length) return "";
        String raw = rowData[colIndex];
        if (raw == null) return "";
        if (raw.startsWith("___SI___")) {
            try {
                int siIndex = Integer.parseInt(raw.substring(8));
                if (siIndex >= 0 && siIndex < sharedStrings.size()) {
                    return sharedStrings.get(siIndex);
                }
            } catch (NumberFormatException ignored) {}
            return "";
        }
        return raw;
    }
}
