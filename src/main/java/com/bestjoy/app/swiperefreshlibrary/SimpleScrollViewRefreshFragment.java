package com.bestjoy.app.swiperefreshlibrary;

import android.view.LayoutInflater;
import android.widget.FrameLayout;

/**
 * 默认是ScrollView
 * Created by bestjoy on 16/7/20.
 */
public class SimpleScrollViewRefreshFragment extends SimpleRefreshFragment{

    protected FrameLayout contentLayout;

    protected int getContentLayout() {
        return R.layout.fragment_simple_scrollview_refresh;
    }

    protected void installContent(LayoutInflater inflater) {
        contentLayout = (FrameLayout) fragmentView.findViewById(R.id.content_layout);
    }
}
