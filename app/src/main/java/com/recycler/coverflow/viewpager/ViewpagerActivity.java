package com.recycler.coverflow.viewpager;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;

import com.recycler.coverflow.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 嵌套ViewPager Demo
 *
 * @author Chen Xiaoping (562818444@qq.com)
 * @version RecyclerCoverFlow
 * @Datetime 2017-07-26 15:05
 * @since RecyclerCoverFlow
 */

public class ViewpagerActivity extends AppCompatActivity {

    private ViewPager mViewPager;
    private ViewPagerAdapter mAdapter;

    private List<Fragment> mFragments = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewpager);
        newFragment();
        initViewPager();
    }

    private void newFragment() {
        mFragments.add(MyFragment.newInstance());
        mFragments.add(MyFragment.newInstance());
        mFragments.add(MyFragment.newInstance());
        mFragments.add(MyFragment.newInstance());
    }

    private void initViewPager() {
        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        mAdapter = new ViewPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(mAdapter);
    }

    class ViewPagerAdapter extends FragmentPagerAdapter {


        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return mFragments.size();
        }

        @Override
        public Fragment getItem(int position) {
            return mFragments.get(position);
        }
    }
}
