package com.recycler.coverflow;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.PagerSnapHelper;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.recycler.coverflow.viewpager.KotlinUtilsKt;

import recycler.coverflow.CoverFlowLayoutManger;
import recycler.coverflow.CoverFlowLayoutManger3;
import recycler.coverflow.RecyclerCoverFlow;

public class JustCoverFlowActivity extends AppCompatActivity implements Adapter.onItemClick {

    private RecyclerCoverFlow mList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_just_coverflow);
        initList();
    }

    private void initList() {
        mList = findViewById(R.id.list);
//        mList.setFlatFlow(true); //平面滚动
        mList.setGreyItem(true); //设置灰度渐变
        mList.setAlphaItem(true); //设置半透渐变

        mList.setLoop(); //循环滚动，注：循环滚动模式暂不支持平滑滚动
        mList.setAdapter(new Adapter(this, this, false));
//        mList.scrollToPosition(5);
        mList.setOnItemSelectedListener(new CoverFlowLayoutManger3.OnSelected() {
            @Override
            public void onItemSelected(int position) {
                ((TextView)findViewById(R.id.index)).setText((position+1)+"/"+mList.getLayoutManager().getItemCount());
                View view = mList.getChildAt(position);
                int tagPos = -10000;
                if(view != null && view.getTag() != null && view.getTag() instanceof CoverFlowLayoutManger3.TAG) {
                    CoverFlowLayoutManger3.TAG tag = (CoverFlowLayoutManger3.TAG)(view.getTag());
                    tagPos = tag.getPos();
                }
                Log.i(KotlinUtilsKt.TAG, " itemCount: " + mList.getLayoutManager().getItemCount() + ", position: " + position + ", tagPos: " + tagPos);
            }
        });
    }

    @Override
    public void clickItem(int pos) {
        mList.smoothScrollToPosition(pos);
    }
}
