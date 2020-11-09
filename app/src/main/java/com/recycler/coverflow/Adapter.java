package com.recycler.coverflow;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.recycler.coverflow.viewpager.KotlinUtilsKt;

import java.util.HashMap;

/**
 * Created by chenxiaoping on 2017/3/28.
 */

public class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {

    private Context mContext;
    private int[] mColors = {R.mipmap.item1,R.mipmap.item2,R.mipmap.item3,R.mipmap.item4, R.mipmap.item5, R.mipmap.item6};
    HashMap<Integer, ViewHolder> viewHolders = new HashMap();

    private onItemClick clickCb;
    private boolean is3D;

    public Adapter(Context c, boolean is3D) {
        mContext = c;
        this.is3D = is3D;
    }

    public Adapter(Context c, onItemClick cb, boolean is3D) {
        mContext = c;
        clickCb = cb;
        this.is3D = is3D;
    }

    public void setOnClickLstn(onItemClick cb) {
        this.clickCb = cb;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layout = R.layout.layout_item;
        if (is3D) layout = R.layout.layout_item_mirror;
        View v = LayoutInflater.from(mContext).inflate(layout, parent, false);
        ViewHolder viewHolder =  new ViewHolder(v);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        viewHolders.put(position, holder);
        Glide.with(mContext).load(mColors[position]).placeholder(R.mipmap.default_load_icon_medium).into(holder.img);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(mContext, "点击了："+position, Toast.LENGTH_SHORT).show();
                if (clickCb != null) {
                    clickCb.clickItem(position);
                }
            }
        });
        Log.i(KotlinUtilsKt.TAG, " onBindViewHolder , position: " + position);
    }

    @Override
    public int getItemCount() {
        return mColors.length;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView img;
        public ViewHolder(View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.img);
        }
    }

    interface onItemClick {
        void clickItem(int pos);
    }
}
