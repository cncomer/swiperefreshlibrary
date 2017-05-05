package com.bestjoy.app.swiperefreshlibrary;

import android.view.View;
import android.widget.FrameLayout;

/**
 * 默认是ScrollView
 * Created by bestjoy on 16/7/20.
 */
public abstract class SimpleScrollViewRefreshFragment extends SimpleRefreshFragment{

    protected FrameLayout contentLayout;

    protected int getContentLayout() {
        return R.layout.fragment_simple_scrollview_refresh;
    }

    protected void initSwipeRefreshLayoutView(View view) {
        super.initSwipeRefreshLayoutView(view);
        contentLayout = (FrameLayout) view.findViewById(R.id.content_layout);
    }
}
