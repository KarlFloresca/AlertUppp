package com.example.alertuppp.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.R;
import com.example.alertuppp.model.EvacuationCenter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class OfficialCenterAdapter extends RecyclerView.Adapter<OfficialCenterAdapter.VH> {

    public interface Listener {
        void onEdit(EvacuationCenter center);
        void onDelete(EvacuationCenter center);
        void onViewOnMap(EvacuationCenter center);
    }

    private List<EvacuationCenter> items = new ArrayList<>();
    private final Listener listener;

    public OfficialCenterAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<EvacuationCenter> data) {
        this.items = new ArrayList<>(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_center_official, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        EvacuationCenter c = items.get(position);

        h.tvName.setText(c.getName());
        h.tvAddress.setText("📍 " + c.getAddress() + " · " + c.getMunicipality());
        h.tvCapacity.setText(c.getCapacityLabel());

        if (c.isFull()) {
            h.tvStatus.setText("FULL");
            h.tvStatus.setBackgroundResource(R.drawable.badge_full_bg);
        } else {
            h.tvStatus.setText("Available");
            h.tvStatus.setBackgroundResource(R.drawable.badge_available_bg);
        }

        // Capacity bar
        float pct = c.getOccupancyPercent();
        int color = pct >= 0.9f ? Color.parseColor("#D32F2F")
                  : pct >= 0.7f ? Color.parseColor("#F57C00")
                  : Color.parseColor("#388E3C");
        h.capacityFill.setBackgroundColor(color);
        // Set width proportionally via post
        h.capacityFill.post(() -> {
            ViewGroup.LayoutParams lp = h.capacityFill.getLayoutParams();
            lp.width = (int) (h.capacityFill.getParent() instanceof View
                    ? ((View) h.capacityFill.getParent()).getWidth() * pct : 0);
            h.capacityFill.setLayoutParams(lp);
        });

        h.btnViewOnMap.setOnClickListener(v -> listener.onViewOnMap(c));
        h.btnEdit.setOnClickListener(v -> listener.onEdit(c));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(c));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress, tvStatus, tvCapacity;
        View capacityFill;
        MaterialButton btnViewOnMap, btnEdit, btnDelete;

        VH(@NonNull View v) {
            super(v);
            tvName       = v.findViewById(R.id.tvCenterName);
            tvAddress    = v.findViewById(R.id.tvCenterAddress);
            tvStatus     = v.findViewById(R.id.tvCenterStatus);
            tvCapacity   = v.findViewById(R.id.tvCapacityCount);
            capacityFill = v.findViewById(R.id.capacityFill);
            btnViewOnMap = v.findViewById(R.id.btnViewOnMap);
            btnEdit      = v.findViewById(R.id.btnEditCenter);
            btnDelete    = v.findViewById(R.id.btnDeleteCenter);
        }
    }
}
