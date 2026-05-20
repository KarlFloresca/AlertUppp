package com.example.alertuppp.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.R;
import com.example.alertuppp.model.EvacuationCenter;

import java.util.ArrayList;
import java.util.List;

public class DashboardCenterAdapter extends RecyclerView.Adapter<DashboardCenterAdapter.VH> {

    private List<EvacuationCenter> items = new ArrayList<>();

    public void setData(List<EvacuationCenter> data) {
        this.items = new ArrayList<>(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_center_dashboard_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        EvacuationCenter c = items.get(position);
        h.tvName.setText(c.getName());
        h.tvCapacity.setText(c.getCurrentOccupancy() + " / " + c.getMaxCapacity());
        
        float pct = c.getOccupancyPercent();
        h.tvPercent.setText((int)(pct * 100) + "%");
        
        int color = pct >= 0.9f ? Color.parseColor("#D32F2F") 
                  : pct >= 0.7f ? Color.parseColor("#F57C00") 
                  : Color.parseColor("#388E3C");
        h.tvPercent.setTextColor(color);
        
        h.statusIndicator.setBackgroundColor(color);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvCapacity, tvPercent;
        View statusIndicator;
        VH(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvCenterName);
            tvCapacity = v.findViewById(R.id.tvCapacityCount);
            tvPercent = v.findViewById(R.id.tvOccupancyPercent);
            statusIndicator = v.findViewById(R.id.statusIndicator);
        }
    }
}
