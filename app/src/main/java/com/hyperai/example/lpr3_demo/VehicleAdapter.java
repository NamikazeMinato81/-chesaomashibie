package com.hyperai.example.lpr3_demo;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 车辆列表 RecyclerView 适配器
 */
public class VehicleAdapter extends RecyclerView.Adapter<VehicleAdapter.ViewHolder> {

    private List<Vehicle> vehicleList;
    private Context context;
    private OnItemClickListener onEditClickListener;
    private OnItemClickListener onDeleteClickListener;

    public interface OnItemClickListener {
        void onItemClick(Vehicle vehicle, int position);
    }

    public VehicleAdapter(Context context, List<Vehicle> vehicleList) {
        this.context = context;
        this.vehicleList = vehicleList;
    }

    public void setOnEditClickListener(OnItemClickListener listener) {
        this.onEditClickListener = listener;
    }

    public void setOnDeleteClickListener(OnItemClickListener listener) {
        this.onDeleteClickListener = listener;
    }

    public void updateData(List<Vehicle> newList) {
        this.vehicleList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_vehicle, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Vehicle vehicle = vehicleList.get(position);

        // 车牌号
        holder.tvPlateNumber.setText(vehicle.getPlateNumber());

        // 内部/外部标签
        if (vehicle.isInternal()) {
            holder.tvTag.setText("内部");
            GradientDrawable drawable = (GradientDrawable) ContextCompat.getDrawable(context, R.drawable.bg_tag_internal);
            holder.tvTag.setBackground(drawable);
        } else {
            holder.tvTag.setText("外部");
            GradientDrawable drawable = (GradientDrawable) ContextCompat.getDrawable(context, R.drawable.bg_tag_external);
            holder.tvTag.setBackground(drawable);
        }

        // 车主姓名
        String ownerName = vehicle.getOwnerName();
        if (ownerName != null && !ownerName.isEmpty()) {
            holder.tvOwnerName.setText("车主：" + ownerName);
            holder.tvOwnerName.setVisibility(View.VISIBLE);
        } else {
            holder.tvOwnerName.setVisibility(View.GONE);
        }

        // 备注
        String remark = vehicle.getRemark();
        if (remark != null && !remark.isEmpty()) {
            holder.tvRemark.setText("备注：" + remark);
            holder.tvRemark.setVisibility(View.VISIBLE);
        } else {
            holder.tvRemark.setVisibility(View.GONE);
        }

        // 编辑按钮
        holder.btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onEditClickListener != null) {
                    onEditClickListener.onItemClick(vehicle, holder.getAdapterPosition());
                }
            }
        });

        // 删除按钮
        holder.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onDeleteClickListener != null) {
                    onDeleteClickListener.onItemClick(vehicle, holder.getAdapterPosition());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return vehicleList == null ? 0 : vehicleList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPlateNumber;
        TextView tvTag;
        TextView tvOwnerName;
        TextView tvRemark;
        Button btnEdit;
        Button btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPlateNumber = itemView.findViewById(R.id.tv_plate_number);
            tvTag = itemView.findViewById(R.id.tv_tag);
            tvOwnerName = itemView.findViewById(R.id.tv_owner_name);
            tvRemark = itemView.findViewById(R.id.tv_remark);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
