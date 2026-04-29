package com.example.alertuppp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.R;
import com.example.alertuppp.model.IncidentReport;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {

    public interface OnReportActionListener {
        void onVerify(IncidentReport report);
        void onAssign(IncidentReport report);
        void onResolve(IncidentReport report);
    }

    private List<IncidentReport> reports = new ArrayList<>();
    private final boolean showOfficialActions;
    private OnReportActionListener listener;

    public ReportAdapter(boolean showOfficialActions) {
        this.showOfficialActions = showOfficialActions;
    }

    public void setListener(OnReportActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<IncidentReport> data) {
        this.reports = data;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        IncidentReport r = reports.get(position);

        h.tvTypeIcon.setText(r.getTypeEmoji());
        h.tvTitle.setText(r.getTitle());
        h.tvMeta.setText(r.getTypeLabel() + " · " + formatTime(r.getCreatedAt()));
        h.tvDescription.setText(r.getDescription() != null ? r.getDescription() : "");

        // Status badge
        String status = r.getStatus() != null ? r.getStatus() : "pending";
        switch (status) {
            case "verified":
                h.tvStatus.setText("Verified");
                h.tvStatus.setBackgroundResource(R.drawable.bg_status_evacuated);
                h.tvStatus.setTextColor(0xFF1976D2);
                break;
            case "ongoing":
                h.tvStatus.setText("Ongoing");
                h.tvStatus.setBackgroundResource(R.drawable.bg_info_card);
                h.tvStatus.setTextColor(0xFF1565C0);
                break;
            case "resolved":
                h.tvStatus.setText("Resolved");
                h.tvStatus.setBackgroundResource(R.drawable.bg_status_safe);
                h.tvStatus.setTextColor(0xFF388E3C);
                break;
            default:
                h.tvStatus.setText("Pending");
                h.tvStatus.setBackgroundResource(R.drawable.bg_status_missing);
                h.tvStatus.setTextColor(0xFFF57C00);
                break;
        }

        if (showOfficialActions && listener != null) {
            h.layoutActions.setVisibility(View.VISIBLE);
            h.btnVerify.setOnClickListener(v -> listener.onVerify(r));
            h.btnAssign.setOnClickListener(v -> listener.onAssign(r));
            h.btnResolve.setOnClickListener(v -> listener.onResolve(r));

            // Hide verify if already past pending
            h.btnVerify.setEnabled("pending".equals(status));
            h.btnVerify.setAlpha("pending".equals(status) ? 1f : 0.4f);
            h.btnResolve.setEnabled(!"resolved".equals(status));
            h.btnResolve.setAlpha(!"resolved".equals(status) ? 1f : 0.4f);
        } else {
            h.layoutActions.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() { return reports.size(); }

    private String formatTime(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try { return iso.substring(0, 10); } catch (Exception e) { return iso; }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTypeIcon, tvTitle, tvMeta, tvDescription, tvStatus;
        LinearLayout layoutActions;
        MaterialButton btnVerify, btnAssign, btnResolve;

        ViewHolder(@NonNull View v) {
            super(v);
            tvTypeIcon    = v.findViewById(R.id.tvReportTypeIcon);
            tvTitle       = v.findViewById(R.id.tvReportTitle);
            tvMeta        = v.findViewById(R.id.tvReportMeta);
            tvDescription = v.findViewById(R.id.tvReportDescription);
            tvStatus      = v.findViewById(R.id.tvReportStatus);
            layoutActions = v.findViewById(R.id.layoutOfficialActions);
            btnVerify     = v.findViewById(R.id.btnVerifyReport);
            btnAssign     = v.findViewById(R.id.btnAssignResponse);
            btnResolve    = v.findViewById(R.id.btnResolveReport);
        }
    }
}
