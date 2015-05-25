package com.bestjoy.app.swiperefreshlibrary;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.shwy.bestjoy.ComApplication;
import com.shwy.bestjoy.utils.AdapterWrapper;
import com.shwy.bestjoy.utils.AsyncTaskCompat;
import com.shwy.bestjoy.utils.AsyncTaskUtils;
import com.shwy.bestjoy.utils.DebugUtils;
import com.shwy.bestjoy.utils.InfoInterface;
import com.shwy.bestjoy.utils.NetworkUtils;
import com.shwy.bestjoy.utils.PageInfo;
import com.shwy.bestjoy.utils.Query;
import com.shwy.bestjoy.utils.ServiceResultObject;

import java.io.InputStream;
import java.util.List;

/*
支持下拉刷新控件的ActionBarActivity基类
 */
public abstract class SwipeRefreshLayoutBaseActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemClickListener{
    private static final String TAG = "SwipeRefreshLayoutBaseFragment";
    protected SwipeRefreshLayout mSwipeLayout;
    protected Context mContext;
    protected ListView mScrollableView;

    protected View mEmptyView;
    private Query mQuery;
    private AdapterWrapper<? extends BaseAdapter> mAdapterWrapper;
    private ContentResolver mContentResolver;

    /**第一次刷新*/
    protected boolean mIsFirstRefresh= false;
    protected boolean mDestroyed = false;
    private View mLoadMoreFootView;
    protected long mLastRefreshTime = -1, mLastClickTitleTime = -1;
    /**如果导航回该界面，从上次刷新以来已经10分钟了，那么自动开始刷新*/
    private static final int MAX_REFRESH_TIME = 1000 * 60 * 10;

    private ProgressBar mFooterViewProgressBar;
    private TextView mFooterViewStatusText;
    protected boolean mIsUpdate = false;

    protected boolean isNeedRequestAgain = false;
    /**如果当前在列表底部了*/
    protected boolean mIsAtListBottom = false;
    private PowerManager.WakeLock mWakeLock;
    private Handler mHandle;
    /**刷新成功*/
    public static final int REFRESH_RESULT_OK = 1;
    /**刷新失败*/
    public static final int REFRESH_RESULT_FAILED = -1000;
    /**刷新成功，没有更多数据了*/
    public static final int REFRESH_RESULT_NO_MORE_DATE = -1001;

    //子类必须实现的方法
    /**提供一个CursorAdapter类的包装对象*/
    protected abstract AdapterWrapper<? extends BaseAdapter> getAdapterWrapper();
    /**检查intent是否包含必须数据，如果没有将finish自身*/
//	protected abstract boolean checkIntent(Intent intent);
    /**返回本地的Cursor*/
    protected abstract Cursor loadLocal(ContentResolver contentResolver);
    protected abstract int savedIntoDatabase(ContentResolver contentResolver, List<? extends InfoInterface> infoObjects);
    protected abstract List<? extends InfoInterface> getServiceInfoList(InputStream is, PageInfo pageInfo);
    protected abstract Query getQuery();
    protected abstract void onRefreshStart();
    protected abstract void onRefreshEnd();
    protected void onRefreshPostEnd() {}
    protected void onLoadLocalStart() {}
    protected void onLoadLocalEnd() {}
    /**返回可滚动View,可以是ListView、ScrollView等*/
    protected View getScrollableView() {
        return mScrollableView;
    }
    protected int getContentLayout() {
        return R.layout.swipe_refresh_layout_base;
    }

    protected abstract InputStream openConnection(String url) throws Exception;
    /***
     * 构建分页查询，默认是mQuery.qServiceUrl&pageindex=&pagesize=的形式
     * @return
     */
    protected abstract String buildPageQuery(Query query);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        mHandle = new Handler();
        mContentResolver = mContext.getContentResolver();
        setContentView(getContentLayout());
        initView();
    }

    private void initView() {
        mSwipeLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeLayout.setOnRefreshListener(this);
        mSwipeLayout.setColorScheme(R.color.holo_blue_bright, R.color.holo_green_light,
                R.color.holo_orange_light, R.color.holo_red_light);

        mScrollableView = (ListView) findViewById(R.id.scrollview);

        mEmptyView = findViewById(android.R.id.empty);
        mAdapterWrapper = getAdapterWrapper();

        ListView listview = (ListView) mScrollableView;

        listview.setOnItemClickListener(this);
        addFooterView();
        updateFooterView(false, null);
        listview.setAdapter(mAdapterWrapper.getAdapter());
//        listview.setEmptyView(mEmptyView);
        removeFooterView();

        mIsFirstRefresh = true;

        listview.setOnScrollListener(new AbsListView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE && mIsAtListBottom && !mIsUpdate) {
                    DebugUtils.logExchangeBC(TAG, "we go to load more.");
                    if (isNeedRequestAgain) {
                        updateFooterView(true, null);
                        if (mQuery == null) {
                            mQuery = getQuery();
                            if (mQuery.mPageInfo == null) {
                                mQuery.mPageInfo = new PageInfo();
                            }
                            int count = mAdapterWrapper.getCount();
                            mQuery.mPageInfo.computePageSize(count);
                        }
                        new QueryServiceTask().execute();
                    } else {
                        DebugUtils.logExchangeBC(TAG, "isNeedRequestAgain is false, we not need to load more");
                    }
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (totalItemCount > 0) {
                    if (firstVisibleItem == 0 && firstVisibleItem + visibleItemCount == totalItemCount) {
                        mIsAtListBottom = true;
                    } else if (firstVisibleItem > 0 && firstVisibleItem + visibleItemCount < totalItemCount) {
                        mIsAtListBottom = false;
                    } else {
                        mIsAtListBottom = true;
                    }

                } else {
                    mIsAtListBottom = false;
                }

            }

        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isNeedLoadLocalOnResume()) {
            loadLocalDataAsync();
        }
        if (isNeedForceRefreshOnResume()) {
            //手动刷新一次
            mHandle.postDelayed(new Runnable() {
                @Override
                public void run() {
                    forceRefresh();
                }
            }, 250);
        }
    }
    /**
     * 当Activity onResume时候是否要做一次强制刷新，默认实现是 如果导航回该界面，从上次刷新以来已经10分钟了，那么自动开始刷新
     * @return
     */
    protected boolean isNeedForceRefreshOnResume() {
        long resumTime = System.currentTimeMillis();
        return resumTime - mLastRefreshTime > MAX_REFRESH_TIME;
    }

    protected boolean isNeedLoadLocalOnResume() {
        return true;
    }

    public void forceRefresh() {
        //手动刷新一次
        onRefresh();
    }

    @Override
    public void onRefresh() {
        mSwipeLayout.setRefreshing(true);
        //重设为0，这样我们可以从头开始更新数据
        if (mQuery == null) {
            mQuery = getQuery();
            if (mQuery.mPageInfo == null) {
                mQuery.mPageInfo = new PageInfo();
            }
        }
        mQuery.mPageInfo.reset();
        isNeedRequestAgain = true;
        int count = mAdapterWrapper.getCount();
        mQuery.mPageInfo.computePageSize(count);
        // Do work to refresh the list here.
        loadServerDataAsync();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDestroyed = true;
        AsyncTaskUtils.cancelTask(mQueryServiceTask);
        AsyncTaskUtils.cancelTask(mLoadLocalTask);
        if (mAdapterWrapper != null) mAdapterWrapper.releaseAdapter();
    }

    private void addFooterView() {
        if (mScrollableView instanceof ListView) {
            ListView listview = (ListView) mScrollableView;
            if (mLoadMoreFootView != null) return;
            if (mLoadMoreFootView == null) {
                mLoadMoreFootView = LayoutInflater.from(mContext).inflate(R.layout.load_more_footer, listview, false);
                mFooterViewProgressBar = (ProgressBar) mLoadMoreFootView.findViewById(R.id.load_more_progressBar);
                mFooterViewStatusText = (TextView) mLoadMoreFootView.findViewById(R.id.load_more_text);
            }

            listview.addFooterView(mLoadMoreFootView, true, false);
        }

    }

    private void removeFooterView() {
        if (mScrollableView instanceof ListView) {
            ListView listview = (ListView) mScrollableView;
            if (mLoadMoreFootView != null) {
                listview.removeFooterView(mLoadMoreFootView);
                mLoadMoreFootView = null;
            }
        }

    }

    private void updateFooterView(boolean loading, String status) {
        if (mLoadMoreFootView == null) {
            addFooterView();
        }
        if (loading) {
            mFooterViewProgressBar.setVisibility(View.VISIBLE);
        } else {
            mFooterViewProgressBar.setVisibility(View.GONE);
        }
        if (!TextUtils.isEmpty(status)) mFooterViewStatusText.setText(status);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View itemView, int position, long id) {
        //子类实现具体的动作
    }


    private LoadLocalTask mLoadLocalTask;
    public void loadLocalDataAsync() {
        AsyncTaskUtils.cancelTask(mLoadLocalTask);
        mLoadLocalTask = new LoadLocalTask();
        mLoadLocalTask.execute();
    }
    private class LoadLocalTask extends AsyncTaskCompat<Void, Void, Cursor> {

        @Override
        protected Cursor doInBackground(Void... params) {
            DebugUtils.logD(TAG, "LoadLocalTask load local data....");
            onLoadLocalStart();
            return loadLocal(mContentResolver);
        }

        @Override
        protected void onPostExecute(Cursor result) {
            super.onPostExecute(result);
            mAdapterWrapper.changeCursor(result);
            int requestCount = 0;
            if (result != null) {
                requestCount = result.getCount();
            }
            if (mEmptyView != null) {
                mEmptyView.setVisibility(requestCount>0?View.GONE:View.VISIBLE);
            }
            onLoadLocalEnd();
            DebugUtils.logD(TAG, "LoadLocalTask load local data finish....localCount is " + requestCount);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

    }

    private QueryServiceTask mQueryServiceTask;
    private void loadServerDataAsync() {
        AsyncTaskUtils.cancelTask(mQueryServiceTask);
        mQueryServiceTask = new QueryServiceTask();
        mQueryServiceTask.execute();
    }

    /**更新或是新增的总数 >0表示有更新数据，需要刷新，=-1网络问题， =-2 已是最新数据 =0 没有更多数据*/
    private class QueryServiceTask extends AsyncTaskCompat<Void, Void, ServiceResultObject> {
        private InputStream _is;

        @Override
        protected ServiceResultObject doInBackground(Void... arg0) {
            mIsUpdate = true;
            ServiceResultObject serviceResultObject = new ServiceResultObject();
            int insertOrUpdateCount = 0;
            try {
                if (mQuery.mPageInfo.mPageIndex == PageInfo.DEFAULT_PAGEINDEX) {
                    //开始刷新
                    onRefreshStart();
                    if (mIsFirstRefresh) {
                        mIsFirstRefresh = false;
                        final Cursor cursor = loadLocal(mContentResolver);
                        if (cursor != null) {
                            mQuery.mPageInfo.computePageSize(cursor.getCount());
                            cursor.close();
                        }
                    }

                }
//				while (isNeedRequestAgain) {
                DebugUtils.logD(TAG, "start pageIndex " + mQuery.mPageInfo.mPageIndex + " pageSize = " + mQuery.mPageInfo.mPageSize);
                _is = openConnection(buildPageQuery(mQuery));

                if (_is != null) {
                    DebugUtils.logD(TAG, "begin parseList....");
                    List<? extends InfoInterface> serviceInfoList = getServiceInfoList(_is, mQuery.mPageInfo);
                    int newCount = serviceInfoList.size();
                    DebugUtils.logD(TAG, "find new date #count = " + newCount + " totalSize = " + mQuery.mPageInfo.mTotalCount);
                    if (mQuery.mPageInfo.mPageIndex == PageInfo.DEFAULT_PAGEINDEX) {
                        onRefreshEnd();
                    }

                    if (newCount == 0) {
                        DebugUtils.logD(TAG, "no more date");
                        isNeedRequestAgain = false;
                        serviceResultObject.mStatusCode = REFRESH_RESULT_NO_MORE_DATE;
                        serviceResultObject.mStatusMessage = mContext.getString(R.string.msg_nonew_for_receive);
                        return serviceResultObject;
                    }
                    if (mQuery.mPageInfo.mTotalCount <= mQuery.mPageInfo.mPageIndex * mQuery.mPageInfo.mPageSize) {
                        DebugUtils.logD(TAG, "returned data count is less than that we requested, so not need to pull data again");
                        isNeedRequestAgain = false;
                    }

                    DebugUtils.logD(TAG, "begin insert or update local database");
                    insertOrUpdateCount = savedIntoDatabase(mContentResolver, serviceInfoList);
                    if (mQuery.mPageInfo.mTotalCount == insertOrUpdateCount) {
                        DebugUtils.logD(TAG, "returned data count is equal to insertOrUpdateCount, so not need to pull data again");
                        isNeedRequestAgain = false;
                    }
                    if (isNeedRequestAgain) {
                        mQuery.mPageInfo.mPageIndex+=1;
                    }
                    serviceResultObject.mStatusMessage = String.valueOf(insertOrUpdateCount);
                } else {
                    DebugUtils.logD(TAG, "finish task due to openContectionLocked return null InputStream");
                    isNeedRequestAgain = false;
                    serviceResultObject.mStatusCode = REFRESH_RESULT_FAILED;
                    serviceResultObject.mStatusMessage = "openContectionLocked return null InputStream";
                }

            } catch (Exception e) {
                e.printStackTrace();
                serviceResultObject.mStatusCode = REFRESH_RESULT_FAILED;
                serviceResultObject.mStatusMessage = ComApplication.getInstance().getGeneralErrorMessage(e);
            } finally {
                NetworkUtils.closeInputStream(_is);
            }

            return serviceResultObject;

        }

        @Override
        protected void onPostExecute(ServiceResultObject result) {
            super.onPostExecute(result);
            if (mDestroyed) return;

            notifyRefreshResult(result.mStatusCode, result.mStatusMessage);

            removeFooterView();

            mLastRefreshTime = System.currentTimeMillis();
            mSwipeLayout.setRefreshing(false);
            mIsUpdate = false;
            onRefreshPostEnd();
            loadLocalDataAsync();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

    }

    /**
     * 子类可以覆盖该方法处理刷新结果显示，如刷新失败，数据已是最新等
     * @param statusCode 状态码
     */
    protected void notifyRefreshResult(int statusCode, String statusMessage) {
        switch (statusCode) {
            case REFRESH_RESULT_FAILED:
                ComApplication.getInstance().showMessage(statusMessage);
                break;
            case REFRESH_RESULT_NO_MORE_DATE:
                ComApplication.getInstance().showMessage(statusMessage);
                break;
            case REFRESH_RESULT_OK:
                break;
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                Intent upIntent = NavUtils.getParentActivityIntent(this);
                if (upIntent == null) {
                    // If we has configurated parent Activity in bgAndroidManifest.xml, we just finish current Activity.
                    finish();
                    return true;
                }
                if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                    // This activity is NOT part of this app's task, so create a new task
                    // when navigating up, with a synthesized back stack.
                    TaskStackBuilder.create(this)
                            // Add all of this activity's parents to the back stack
                            .addNextIntentWithParentStack(upIntent)
                                    // Navigate up to the closest parent
                            .startActivities();
                } else {
                    // This activity is part of this app's task, so simply
                    // navigate up to the logical parent activity.
                    NavUtils.navigateUpTo(this, upIntent);
                }
                return true;
            default :
                return super.onOptionsItemSelected(item);
        }

    }

}
