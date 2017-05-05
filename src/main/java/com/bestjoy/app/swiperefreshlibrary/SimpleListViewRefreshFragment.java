package com.bestjoy.app.swiperefreshlibrary;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

/**
 * 默认是ScrollView
 * Created by bestjoy on 16/7/20.
 */
public abstract class SimpleListViewRefreshFragment extends SimpleRefreshFragment implements AdapterView.OnItemClickListener{

    protected ListView listView;

    protected int getContentLayout() {
        return R.layout.swipe_refresh_layout_base;
    }

    protected void initSwipeRefreshLayoutView(View view) {
        super.initSwipeRefreshLayoutView(view);
        listView = (ListView) view.findViewById(R.id.scrollview);
        listView.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

    }
}
