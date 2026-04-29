package com.example.alertuppp.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.R;
import com.example.alertuppp.model.FamilyMember;

import java.util.List;

public class FamilyMemberAdapter extends RecyclerView.Adapter<FamilyMemberAdapter.VH> {

    public interface Listener {
        void onEdit(FamilyMember member, int position);
        void onDelete(FamilyMember member, int position);
    }

    private final List<FamilyMember> members;
    private final Listener listener;

    public FamilyMemberAdapter(List<FamilyMember> members, Listener listener) {
        this.members = members;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_family_member, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FamilyMember m = members.get(position);
        String name = m.getFullName() != null ? m.getFullName() : "?";

        h.tvAvatar.setText(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());
        h.tvName.setText(name);

        // Age line
        if (m.getAge() > 0) {
            h.tvAge.setText("Age " + m.getAge());
        } else {
            h.tvAge.setText("Age not set");
        }

        // Notes (only show if non-empty)
        if (!TextUtils.isEmpty(m.getNotes())) {
            h.tvNotes.setText("📝 " + m.getNotes());
            h.tvNotes.setVisibility(View.VISIBLE);
        } else {
            h.tvNotes.setVisibility(View.GONE);
        }

        h.btnEdit.setOnClickListener(v -> listener.onEdit(m, h.getAdapterPosition()));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(m, h.getAdapterPosition()));
    }

    @Override
    public int getItemCount() { return members.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvName, tvAge, tvNotes;
        ImageButton btnEdit, btnDelete;

        VH(@NonNull View v) {
            super(v);
            tvAvatar  = v.findViewById(R.id.tvMemberAvatar);
            tvName    = v.findViewById(R.id.tvMemberName);
            tvAge     = v.findViewById(R.id.tvMemberAge);
            tvNotes   = v.findViewById(R.id.tvMemberNotes);
            btnEdit   = v.findViewById(R.id.btnEditMember);
            btnDelete = v.findViewById(R.id.btnDeleteMember);
        }
    }
}
