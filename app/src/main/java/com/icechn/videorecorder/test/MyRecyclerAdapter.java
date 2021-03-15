package com.icechn.videorecorder.test;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.icechn.videorecorder.R;

import java.util.List;

/**
 * Created by linkaipeng on 2016/7/1.
 */
public class MyRecyclerAdapter extends RecyclerView.Adapter {

    private Context mContext;
    private List<String> mPerformanceModelList;

    public MyRecyclerAdapter(Context context, List<String> performanceModelList) {
        mContext = context;
        mPerformanceModelList = performanceModelList;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ItemViewHolder(LayoutInflater.from(mContext).inflate(R.layout.item_anim_layout, parent, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder == null) {
            return;
        }
        String text = mPerformanceModelList.get(position);
        ItemViewHolder viewHolder = (ItemViewHolder) holder;
        viewHolder.mTimesTextView.setText(text);
    }

    @Override
    public int getItemCount() {
        if (mPerformanceModelList == null) {
            return 0;
        }
        return mPerformanceModelList.size();
    }

    private static class ItemViewHolder extends RecyclerView.ViewHolder {

        private ImageView mIconImageView;
        private TextView mTimesTextView;

        public ItemViewHolder(View itemView) {
            super(itemView);
            mIconImageView = (ImageView) itemView.findViewById(R.id.item_anim_name_iv);
            mTimesTextView = (TextView) itemView.findViewById(R.id.item_anim_name_tv);
        }
    }
}
