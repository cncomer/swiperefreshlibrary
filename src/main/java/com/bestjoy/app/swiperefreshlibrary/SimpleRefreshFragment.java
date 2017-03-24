package com.bestjoy.app.swiperefreshlibrary;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.shwy.bestjoy.utils.NetworkRequestHelper;

/**
 * 默认是ScrollView
 * Created by bestjoy on 16/7/20.
 */
public class SimpleRefreshFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, View.OnClickListener{


    protected SwipeRefreshLayout swipeRefreshLayout;
    protected boolean isRefreshing= false;
    protected View fragmentView;

    protected int getContentLayout() {
        return R.layout.fragment_simple_refresh;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentView = inflater.inflate(getContentLayout(), container, false);
        initSwipeRefreshLayoutView(fragmentView);
        installContent(inflater);
        return fragmentView;
    }

    /**
     * 初始化SwipeRefreshLayout
     * @param view
     */
    protected void initSwipeRefreshLayoutView(View view) {
        swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setColorScheme(
                R.color.holo_blue_bright,
                R.color.holo_green_light,
                R.color.holo_orange_light,
                R.color.holo_red_light);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE);
    }


    protected void installContent(LayoutInflater inflater) {
    }

    @Override
    public void onRefresh() {
        isRefreshing = true;

        NetworkRequestHelper.requestAsync(new NetworkRequestHelper.IRequestRespond() {
            @Override
            public void onRequestEnd(Object result) {
                isRefreshing = false;
                swipeRefreshLayout.setRefreshing(false);
                refreshEnd(result);
            }

            @Override
            public void onRequestStart() {
            }

            @Override
            public void onRequestCancelled() {
                swipeRefreshLayout.setRefreshing(false);
                isRefreshing = false;
                refreshCanceled();
            }

            @Override
            public Object doInBackground() {
                return refreshInBackground();
            }
        });
    }

    protected Object refreshInBackground() {
        return null;
    }

    protected void refreshEnd(Object result) {
    }

    protected void refreshCanceled() {
    }

    public void forceRefresh() {
        swipeRefreshLayout.setRefreshing(true);
        onRefresh();
    }

    @Override
    public void onClick(View v) {

    }
}
