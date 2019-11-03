package com.github.btlebeacontracker.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.btlebeacontracker.R;

import java.util.List;
import java.util.Objects;

/**
 * Adapter that allows us to render a list of items
 *
 * @author marvinlabs
 */
public class ItemListAdapter extends ArrayAdapter<Item> {

    private final LayoutInflater li;

    /**
     * Constructor from a list of items
     */
    public ItemListAdapter(Context context, List<Item> items) {
        super(context, 0, items);
        li = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {

        // The item we want to get the view for
        // --
        final Item item = getItem(position);
        ((ListView) parent).setItemChecked(position, Objects.requireNonNull(item).isActive());

        // Re-use the view if possible
        // --
        ViewHolder holder;
        if (convertView == null) {
            convertView = li.inflate(R.layout.item, null);
            holder = new ViewHolder(convertView);
            convertView.setTag(R.id.holder, holder);
        } else {
            holder = (ViewHolder) convertView.getTag(R.id.holder);
        }

        // Set some view properties
        holder.id.setText(item.getId());
        holder.caption.setText(item.getCaption());

        // Restore the checked state properly
        final ListView lv = (ListView) parent;
        holder.layout.setChecked(lv.isItemChecked(position));

        return convertView;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private static class ViewHolder {
        final TextView id;
        final TextView caption;
        final CheckableRelativeLayout layout;

        ViewHolder(View root) {
            id = root.findViewById(R.id.itemId);
            caption = root.findViewById(R.id.itemCaption);
            layout = root.findViewById(R.id.layout);
        }
    }
}
