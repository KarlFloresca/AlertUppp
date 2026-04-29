package com.example.alertuppp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.R;
import com.example.alertuppp.model.HouseholdMember;

import java.util.List;

public class HouseholdMemberAdapter extends RecyclerView.Adapter<HouseholdMemberAdapter.ViewHolder> {

    public interface OnMemberActionListener {
        void onEdit(int position);
        void onRemove(int position);
    }

    private final List<HouseholdMember> members;
    private final OnMemberActionListener listener;

    public HouseholdMemberAdapter(List<HouseholdMember> members, OnMemberActionListener listener) {
        this.members = members;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_household_member, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HouseholdMember member = members.get(position);

        holder.tvName.setText(member.getFullName());
        holder.tvRelation.setText(member.getRelationLabel() + " · " + member.getSex() + " · Age " + member.getAge());

        String tags = member.getSpecialNeedsTags();
        if (tags.isEmpty()) {
            holder.tvTags.setVisibility(View.GONE);
        } else {
            holder.tvTags.setVisibility(View.VISIBLE);
            holder.tvTags.setText(tags);
        }

        // Avatar initial
        String name = member.getFullName();
        holder.tvAvatar.setText(name != null && !name.isEmpty()
                ? String.valueOf(name.charAt(0)).toUpperCase() : "?");

        holder.btnEdit.setOnClickListener(v -> listener.onEdit(holder.getAdapterPosition()));
        holder.btnRemove.setOnClickListener(v -> listener.onRemove(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() { return members.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvName, tvRelation, tvTags;
        ImageButton btnEdit, btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar   = itemView.findViewById(R.id.tvMemberAvatar);
            tvName     = itemView.findViewById(R.id.tvMemberName);
            tvRelation = itemView.findViewById(R.id.tvMemberRelation);
            tvTags     = itemView.findViewById(R.id.tvMemberTags);
            btnEdit    = itemView.findViewById(R.id.btnEditMember);
            btnRemove  = itemView.findViewById(R.id.btnRemoveMember);
        }
    }
}
