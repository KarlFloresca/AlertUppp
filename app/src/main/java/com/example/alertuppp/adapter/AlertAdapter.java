package com.example.alertuppp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.R;
import com.example.alertuppp.model.Alert;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.ViewHolder> {

    public interface OnAlertActionListener {
        void onDeactivate(Alert alert);
    }

    private List<Alert> alerts = new ArrayList<>();
    private final boolean showOfficialActions;
    private OnAlertActionListener listener;

    /** @param showOfficialActions true for official view (shows deactivate button) */
    public AlertAdapter(boolean showOfficialActions) {
        this.showOfficialActions = showOfficialActions;
    }

    public void setListener(OnAlertActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<Alert> data) {
        this.alerts = data;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alert_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Alert a = alerts.get(position);

        h.tvTitle.setText(a.getTitle());
        h.tvArea.setText(a.getArea() != null ? a.getArea() : "Province Wide");
        h.tvBody.setText(a.getBody());
        h.tvLevel.setText(a.getLevelLabel() != null ? a.getLevelLabel().toUpperCase() : "INFO");
        h.tvTime.setText(formatTime(a.getIssuedAt()));

        // Color by level (Status Sidebar)
        int color;
        switch (a.getLevel() != null ? a.getLevel().toLowerCase() : "info") {
            case "danger":
            case "critical":
                color = 0xFFD32F2F; // Red
                break;
            case "warning":
                color = 0xFFF57C00; // Orange
                break;
            default:
                color = 0xFF1976D2; // Blue
                break;
        }
        h.viewSeverity.setBackgroundColor(color);
        h.tvLevel.setTextColor(color);
        h.tvLevel.setBackgroundResource(a.getLevel() != null && (a.getLevel().equalsIgnoreCase("danger") || a.getLevel().equalsIgnoreCase("critical")) 
                ? R.drawable.bg_status_missing : R.drawable.bg_status_safe);

        // Official actions
        if (showOfficialActions && listener != null) {
            h.layoutActions.setVisibility(View.VISIBLE);
            h.btnDeactivate.setOnClickListener(v -> listener.onDeactivate(a));
        } else {
            h.layoutActions.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() { return alerts.size(); }

    private String formatTime(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            // "2023-10-27T10:00:00Z" -> "Oct 27, 10:00"
            return iso.substring(0, 10) + " " + iso.substring(11, 16);
        } catch (Exception e) {
            return iso;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.card.MaterialCardView card;
        View viewSeverity;
        LinearLayout layoutActions;
        TextView tvTitle, tvArea, tvBody, tvLevel, tvTime;
        MaterialButton btnDeactivate;

        ViewHolder(@NonNull View v) {
            super(v);
            card           = v.findViewById(R.id.alertCard);
            viewSeverity   = v.findViewById(R.id.viewAlertSeverity);
            tvTitle        = v.findViewById(R.id.tvAlertTitle);
            tvArea         = v.findViewById(R.id.tvAlertArea);
            tvBody         = v.findViewById(R.id.tvAlertBody);
            tvLevel        = v.findViewById(R.id.tvAlertLevel);
            tvTime         = v.findViewById(R.id.tvAlertTime);
            layoutActions  = v.findViewById(R.id.layoutAlertActions);
            btnDeactivate  = v.findViewById(R.id.btnDeactivateAlert);
        }
    }
}
