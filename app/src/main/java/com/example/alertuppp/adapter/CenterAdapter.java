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

public class CenterAdapter extends RecyclerView.Adapter<CenterAdapter.ViewHolder> {

    public interface OnCenterActionListener {
        void onDirections(EvacuationCenter center);
        void onCheckIn(EvacuationCenter center);
        void onViewFamilies(EvacuationCenter center);
    }

    private List<EvacuationCenter> centers = new ArrayList<>();
    private final OnCenterActionListener listener;

    public CenterAdapter(OnCenterActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<EvacuationCenter> data) {
        this.centers = data;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_center_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        EvacuationCenter c = centers.get(position);

        h.tvName.setText(c.getName());
        h.tvAddress.setText("📍 " + c.getAddress() + " · " + c.getMunicipality());
        h.tvCapacity.setText(c.getCapacityLabel());

        // Status badge
        if (c.isFull()) {
            h.tvStatus.setText("FULL");
            h.tvStatus.setBackgroundResource(R.drawable.badge_full_bg);
        } else {
            h.tvStatus.setText("Available");
            h.tvStatus.setBackgroundResource(R.drawable.badge_available_bg);
        }

        // Capacity bar fill (proportional width via weight trick — set tag for later)
        h.capacityFill.setTag(c.getOccupancyPercent());
        float pct = c.getOccupancyPercent();
        if (pct >= 0.9f) {
            h.capacityFill.setBackgroundColor(Color.parseColor("#D32F2F"));
        } else if (pct >= 0.7f) {
            h.capacityFill.setBackgroundColor(Color.parseColor("#F57C00"));
        } else {
            h.capacityFill.setBackgroundColor(Color.parseColor("#388E3C"));
        }

        // Facilities
        h.tagWater.setVisibility(c.isHasWater() ? View.VISIBLE : View.GONE);
        h.tagFood.setVisibility(c.isHasFood() ? View.VISIBLE : View.GONE);
        h.tagMedical.setVisibility(c.isHasMedical() ? View.VISIBLE : View.GONE);

        h.btnDirections.setOnClickListener(v -> listener.onDirections(c));
        h.btnCheckIn.setOnClickListener(v -> listener.onCheckIn(c));
        h.btnFamilies.setOnClickListener(v -> listener.onViewFamilies(c));

        // Disable check-in if full
        h.btnCheckIn.setEnabled(!c.isFull());
        h.btnCheckIn.setAlpha(c.isFull() ? 0.4f : 1f);
    }

    @Override
    public int getItemCount() { return centers.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress, tvStatus, tvCapacity;
        TextView tagWater, tagFood, tagMedical;
        View capacityFill;
        MaterialButton btnDirections, btnCheckIn, btnFamilies;

        ViewHolder(@NonNull View v) {
            super(v);
            tvName       = v.findViewById(R.id.tvCenterName);
            tvAddress    = v.findViewById(R.id.tvCenterAddress);
            tvStatus     = v.findViewById(R.id.tvCenterStatus);
            tvCapacity   = v.findViewById(R.id.tvCapacityCount);
            tagWater     = v.findViewById(R.id.tagWater);
            tagFood      = v.findViewById(R.id.tagFood);
            tagMedical   = v.findViewById(R.id.tagMedical);
            capacityFill = v.findViewById(R.id.capacityFill);
            btnDirections = v.findViewById(R.id.btnDirections);
            btnCheckIn    = v.findViewById(R.id.btnCheckInCenter);
            btnFamilies   = v.findViewById(R.id.btnViewFamilies);
        }
    }
}
