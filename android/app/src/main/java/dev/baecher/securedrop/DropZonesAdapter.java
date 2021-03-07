package dev.baecher.securedrop;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class DropZonesAdapter extends RecyclerView.Adapter<DropZonesAdapter.ViewHolder> {
    private ArrayList<DropZonesActivity.DropZone> dropZones;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        public ViewHolder(View view) {
            super(view);
            textView = (TextView) view.findViewById(R.id.dropZoneItem);
        }

        public TextView getTextView() {
            return textView;
        }
    }

    public DropZonesAdapter(ArrayList<DropZonesActivity.DropZone> dropZones) {
        updateDropZones(dropZones);
    }

    public void updateDropZones(ArrayList<DropZonesActivity.DropZone> dropZones) {
        this.dropZones = dropZones;
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.drop_zone_item, viewGroup, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        TextView v = viewHolder.getTextView();
        v.setText(dropZones.get(position).name);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(v.getContext(), DropZoneSettingsActivity.class);
                intent.putExtra("dropZoneId", dropZones.get(position).id);
                v.getContext().startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return dropZones.size();
    }
}
