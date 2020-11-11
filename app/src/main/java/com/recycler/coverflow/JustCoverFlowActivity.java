package com.recycler.coverflow;

import android.content.Context;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.recycler.coverflow.viewpager.KotlinUtilsKt;

import recycler.coverflow.CoverFlowLayoutManger;
import recycler.coverflow.CoverFlowLayoutManger3;
import recycler.coverflow.RecyclerCoverFlow;

public class JustCoverFlowActivity extends AppCompatActivity implements Adapter.onItemClick {

    private RecyclerCoverFlow mList;

    public static int dip2px(Context context, float dpValue) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_just_coverflow);
        initList();
    }

    final float[] alphas = new float[]{1f, 0.5f, 0.1f, 0.05f};
    final Adapter adapter = new Adapter(this, this, false);

    private void initList() {
        mList = findViewById(R.id.list);
    //    mList.setFlatFlow(true); //平面滚动
        mList.setGreyItem(true); //设置灰度渐变
        mList.setAlphaItem(true); //设置半透渐变

        mList.setLoop(); //循环滚动，注：循环滚动模式暂不支持平滑滚动
        mList.getCoverFlowLayout().setRecyclerView(mList);

        mList.getCoverFlowLayout().setIntervalDistance(dip2px(this, 50));
        mList.getCoverFlowLayout().setIntervalHeightDistance(dip2px(this, 10));

        mList.setAdapter(adapter);
//        mList.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
//        new PagerSnapHelper().attachToRecyclerView(mList);
//        mList.scrollToPosition(5);

        //参考：https://blog.csdn.net/chunqiuwei/article/details/103187199
        //参考：https://blog.csdn.net/chunqiuwei/article/details/103257452
        mList.setOnFlingListener(new RecyclerView.OnFlingListener() {
            @Override
            public boolean onFling(int velocityX, int velocityY) {
                RecyclerView.LayoutManager layoutManager = mList.getLayoutManager();
                if(layoutManager != null && layoutManager instanceof CoverFlowLayoutManger3) {
                    ((CoverFlowLayoutManger3) layoutManager).fixOffsetWhenFinishScroll();
                }
                return true;
            }
        });

        mList.setOnItemSelectedListener(new CoverFlowLayoutManger3.OnItemScrollListener() {
            @Override
            public void onItemScrolled() {
                if(mList.getCoverFlowLayout() != null) {
                    updateAlpha2("onItemScrolled", 1);
                }
            }

            @Override
            public void onItemSelected(int position) {
                ((TextView)findViewById(R.id.index)).setText((position+1)+"/"+mList.getLayoutManager().getItemCount());
                View view = mList.getChildAt(position);
                int tagPos = -10000;
                if(view != null && view.getTag() != null && view.getTag() instanceof CoverFlowLayoutManger3.TAG) {
                    CoverFlowLayoutManger3.TAG tag = (CoverFlowLayoutManger3.TAG)(view.getTag());
                    tagPos = tag.getPos();
                }
                Log.i(KotlinUtilsKt.TAG, "onItemSelected  itemCount: " + mList.getLayoutManager().getItemCount() +
                        ", selectedPosition: " + position + ", tagPos: " + tagPos);
                updateAlpha2("onItemSelected", 2);
            }
        });
    }

    public int getCenterOffsetChildIndex(int offset) {
        int childCount = mList.getChildCount();
        for(int i = 0; i < childCount; i++) {
            int actualPos = mList.getCoverFlowLayout().getChildActualPos(i);
            if(offset == actualPos) {
                return i;
            }
        }
        return -10000;
    }


    private void updateAlpha2(String logTag, int type) {

        CoverFlowLayoutManger3 layoutManger3 = mList.getCoverFlowLayout();
        int centerPos = layoutManger3.getCenterPosition();
        int selectedPos = layoutManger3.getSelectedPos();

        if(type == 2) {
            centerPos = selectedPos;
        }

        int centerIndex = getCenterOffsetChildIndex(centerPos);

        int min = centerIndex - 2;
        int max = centerIndex + 2;

        Log.i(KotlinUtilsKt.TAG,  logTag + "  updateAlpha2, centerIndex: " + centerIndex);


        for(int i = min; i <= max; i++) {
            int interval = Math.abs(i - centerIndex);
            float alpha = 0.0f;
            if(interval < 3) {
                alpha = alphas[interval];
            }
            int indexOfChild = -10000;
            int tagPos = -10000;
            View view = mList.getChildAt(i);
            if(view != null && mList.findContainingViewHolder(view) != null) {
                RecyclerView.ViewHolder viewHolder = mList.findContainingViewHolder(view);
                if(viewHolder instanceof Adapter.ViewHolder) {
                    ((Adapter.ViewHolder)viewHolder).img.setAlpha(alpha);
                    indexOfChild = mList.indexOfChild(viewHolder.itemView);
                    Object tag = viewHolder.itemView.getTag();
                    if(tag != null && tag instanceof CoverFlowLayoutManger3.TAG) {
                        tagPos = ((CoverFlowLayoutManger3.TAG)tag).getPos();
                    }
                }
            }
            Log.i(KotlinUtilsKt.TAG,  logTag + "  setAlpha, i: " + i + ", indexOfChild: " + indexOfChild + ", tagPos: " + tagPos + ", alpha: " + alpha + ", viewHolder : " + (view != null));
        }
        for(int i = 0;  i < mList.getChildCount(); i++) {
            View view = mList.getChildAt(i);
            RecyclerView.ViewHolder object = mList.findContainingViewHolder(view);
            if(object != null && object instanceof Adapter.ViewHolder) {
                float alpha = ((Adapter.ViewHolder)object).img.getAlpha();
                Log.i(KotlinUtilsKt.TAG, logTag + "  getAlpha, i: " + i + ", alpha: " + alpha);
            }
        }
    }

    @Override
    public void clickItem(int pos) {
        mList.smoothScrollToPosition(pos);
    }
}
