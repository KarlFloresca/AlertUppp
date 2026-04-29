package com.example.alertuppp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.R;
import com.example.alertuppp.model.Family;
import com.example.alertuppp.model.FamilyMember;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class FamilyAdapter extends RecyclerView.Adapter<FamilyAdapter.VH> {

    public interface Listener {
        void onEditFamily(Family family);
        void onDeleteFamily(Family family);
        void onEditMember(Family family, FamilyMember member, int memberPos);
        void onDeleteMember(Family family, FamilyMember member, int memberPos);
    }

    private final List<Family> families;
    private final Listener listener;
    private boolean headMode = true;

    public FamilyAdapter(List<Family> families, Listener listener) {
        this.families = families;
        this.listener = listener;
    }

    public void setHeadMode(boolean headMode) {
        this.headMode = headMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_family_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Family f = families.get(position);

        // Family name as primary label
        String name = f.getFamilyName() != null && !f.getFamilyName().isEmpty()
                ? f.getFamilyName() : "Family";
        h.tvFamilyName.setText("👨‍👩‍👧  " + name);

        // Center as secondary label (optional)
        if (f.getCenterName() != null && !f.getCenterName().isEmpty()) {
            h.tvCenterLabel.setText("📍 " + f.getCenterName());
        } else {
            h.tvCenterLabel.setText("📍 No center assigned");
        }
        h.tvCenterLabel.setVisibility(View.VISIBLE);

        int count = f.getMemberCount();
        h.tvMemberCount.setText(count + " member" + (count == 1 ? "" : "s"));

        // Members nested list
        boolean hasMembers = f.getMemberCount() > 0;
        h.tvNoMembers.setVisibility(hasMembers ? View.GONE : View.VISIBLE);
        h.rvMembers.setVisibility(hasMembers ? View.VISIBLE : View.GONE);
        h.rvMembers.setLayoutManager(new LinearLayoutManager(h.rvMembers.getContext()));
        h.rvMembers.setAdapter(new FamilyMemberAdapter(f.getMembers(),
                new FamilyMemberAdapter.Listener() {
                    @Override public void onEdit(FamilyMember m, int mp)   { listener.onEditMember(f, m, mp); }
                    @Override public void onDelete(FamilyMember m, int mp) { listener.onDeleteMember(f, m, mp); }
                }));

        // Head-only controls
        h.btnEditFamily.setVisibility(headMode ? View.VISIBLE : View.GONE);
        h.btnDeleteFamily.setVisibility(headMode ? View.VISIBLE : View.GONE);
        h.btnEditFamily.setOnClickListener(v -> listener.onEditFamily(f));
        h.btnDeleteFamily.setOnClickListener(v -> listener.onDeleteFamily(f));
    }

    @Override
    public int getItemCount() { return families.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvFamilyName, tvCenterLabel, tvMemberCount, tvNoMembers;
        RecyclerView rvMembers;
        MaterialButton btnEditFamily, btnDeleteFamily;

        VH(@NonNull View v) {
            super(v);
            tvFamilyName   = v.findViewById(R.id.tvFamilyCenterName);   // reused id
            tvCenterLabel  = v.findViewById(R.id.tvFamilyCenterLabel);
            tvMemberCount  = v.findViewById(R.id.tvFamilyMemberCount);
            tvNoMembers    = v.findViewById(R.id.tvNoMembers);
            rvMembers      = v.findViewById(R.id.rvFamilyMembers);
            btnEditFamily  = v.findViewById(R.id.btnEditFamily);
            btnDeleteFamily = v.findViewById(R.id.btnDeleteFamily);
        }
    }
}
