package com.bestjoy.app.swiperefreshlibrary;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;

/*
支持下拉刷新控件的Fragment基类
 */
public abstract class SwipeRefreshLayoutBaseFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemClickListener{
    private static final String TAG = "SwipeRefreshLayoutBaseFragment";
    protected SwipeRefreshLayout mSwipeLayout;
    protected View mScrollableView;

    protected View mEmptyView;
    private Query mQuery;
    private AdapterWrapper<? extends BaseAdapter> mAdapterWrapper;
    protected ContentResolver mContentResolver;

    /**第一次刷新*/
    protected boolean mIsFirstRefresh= false;
    protected boolean mDestroyed = false;
    private View mLoadMoreFootView;
    private long mLastRefreshTime = -1, mLastClickTitleTime = -1;
    /**如果导航回该界面，从上次刷新以来已经10分钟了，那么自动开始刷新*/
    private static final int MAX_REFRESH_TIME = 1000 * 60 * 10;

    private ProgressBar mFooterViewProgressBar;
    private TextView mFooterViewStatusText;
    protected boolean mIsUpdate = false;

    private boolean isNeedRequestAgain = false;
    /**如果当前在列表底部了*/
    protected boolean mIsAtListBottom = false;
    private PowerManager.WakeLock mWakeLock;
    /**刷新成功*/
    public static final int REFRESH_RESULT_OK = 1;
    /**刷新失败*/
    public static final int REFRESH_RESULT_FAILED = -1000;
    /**刷新结果自定义*/
    public static final int REFRESH_RESULT_CUSTOM_OP = -1002;
    /**刷新成功，没有更多数据了*/
    public static final int REFRESH_RESULT_NO_MORE_DATE = -1001;

    private RefreshCallback mRefreshCallback;

    private AbsListView.OnScrollListener onScrollListener;

    //子类必须实现的方法
    /**提供一个CursorAdapter类的包装对象*/
    protected abstract AdapterWrapper<? extends BaseAdapter> getAdapterWrapper();
    /**检查intent是否包含必须数据，如果没有将finish自身*/
//	protected abstract boolean checkIntent(Intent intent);
    /**返回本地的Cursor*/
    protected abstract Cursor loadLocal(ContentResolver contentResolver);
    protected abstract int savedIntoDatabase(ContentResolver contentResolver, List<? extends InfoInterface> infoObjects);
    protected abstract List<? extends InfoInterface> getServiceInfoList(InputStream is, PageInfo pageInfo) throws Exception;
    protected abstract Query getQuery();
    protected abstract void onRefreshStart();
    protected void onRefreshEnd(){}

    /***
     * 总共有几个新数据
     * @param dataCount
     */
    protected void onRefreshEndV2(int dataCount, long dataTotal){
        if (mRefreshCallback != null) {
            mRefreshCallback.onRefresh(dataCount, dataTotal);
        }
        onRefreshEnd();
    }
    protected void onRefreshPostEnd() {}
    protected void onRefreshCanceled(){}
    protected void onLoadLocalEnd() {}
    protected void onLoadLocalStart() {}
    /**返回可滚动View,可以是ListView、ScrollView等*/
    protected View getScrollableView() {
        return mScrollableView;
    }
    protected int getContentLayout() {
        return R.layout.swipe_refresh_layout_base;
    }

    protected abstract InputStream openConnection(String url) throws Exception;
    /**加密JSONObject类型的请求,默认使用URLEncoder.encode*/
    protected String encodeJsonObjectRequest(JSONObject queryObject) {
        return encodeRequest(queryObject.toString());
    }
    /**加密请求,默认使用URLEncoder.encode*/
    protected String encodeRequest(String source) {
        return URLEncoder.encode(source);
    }
    /***
     * 构建分页查询，默认是mQuery.qServiceUrl&pageindex=&pagesize=的形式
     * 如果mExtraData是JSONObject,则表示我们需要在JSONObject中添加分页数据
     * @return
     */
    protected String buildPageQuery(Query query) {
        try {
            if (query.mExtraData instanceof JSONObject) {
                JSONObject queryObject = (JSONObject) query.mExtraData;
                queryObject.put("pageindex", query.mPageInfo.mPageIndex);
                queryObject.put("pagesize", query.mPageInfo.mPageSize);
                if (!query.qServiceUrl.endsWith("?")) {
                    query.qServiceUrl+="?";
                }
                return query.qServiceUrl + "para="+ encodeJsonObjectRequest(queryObject);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        StringBuilder sb = new StringBuilder(query.qServiceUrl);
        if (!query.qServiceUrl.endsWith("?")) {
            sb.append('&');
        }

        sb.append("pageindex=").append(query.mPageInfo.mPageIndex).append('&');
        sb.append("pagesize=").append(query.mPageInfo.mPageSize);
        return sb.toString();
    }


    protected void setOnScrollListener(AbsListView.OnScrollListener listener) {
        onScrollListener = listener;
    }

    protected Handler mHandle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContentResolver = getActivity().getContentResolver();
        mHandle = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(getContentLayout(), container, false);
        mSwipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_refresh_layout);
        mSwipeLayout.setOnRefreshListener(this);
        mSwipeLayout.setColorScheme(R.color.holo_blue_bright, R.color.holo_green_light,
                R.color.holo_orange_light, R.color.holo_red_light);

        mScrollableView = view.findViewById(R.id.scrollview);

        mEmptyView = view.findViewById(android.R.id.empty);
        mAdapterWrapper = getAdapterWrapper();

        if (mScrollableView instanceof ListView) {
            ListView listview = (ListView) mScrollableView;

            listview.setOnItemClickListener(this);
            addFooterView();
            updateFooterView(false, null);
            listview.setAdapter(mAdapterWrapper.getAdapter());
//            listview.setEmptyView(mEmptyView);
            removeFooterView();

            mIsFirstRefresh = true;

            listview.setOnScrollListener(new AbsListView.OnScrollListener() {

                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (onScrollListener != null) {
                        onScrollListener.onScrollStateChanged(view, scrollState);
                    }
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
                            new QueryServiceTask(mQuery).execute();
                        } else {
                            DebugUtils.logExchangeBC(TAG, "isNeedRequestAgain is false, we not need to load more");
                        }
                    }
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (onScrollListener != null) {
                        onScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
                    }
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
        } else  if (mScrollableView instanceof GridView) {
            GridView gridview = (GridView) mScrollableView;

            gridview.setOnItemClickListener(this);
            gridview.setAdapter(mAdapterWrapper.getAdapter());
//            listview.setEmptyView(mEmptyView);
            removeFooterView();
            mIsFirstRefresh = true;
        }

        return view;
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
        AsyncTaskUtils.cancelTask(mQueryServiceTask);
        mSwipeLayout.setRefreshing(true);
        //重设为0，这样我们可以从头开始更新数据
        mQuery = getQuery();
        if (mQuery.mPageInfo == null) {
            mQuery.mPageInfo = new PageInfo();
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
                mLoadMoreFootView = LayoutInflater.from(getActivity().getApplicationContext()).inflate(R.layout.load_more_footer, listview, false);
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
            onLoadLocalStart();
            DebugUtils.logD(TAG, "LoadLocalTask load local data....");
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
    protected void loadServerDataAsync() {
        mQueryServiceTask = new QueryServiceTask(mQuery);
        mQueryServiceTask.execute();
    }

    /**更新或是新增的总数 >0表示有更新数据，需要刷新，=-1网络问题， =-2 已是最新数据 =0 没有更多数据*/
    private class QueryServiceTask extends AsyncTaskCompat<Void, Void, ServiceResultObject> {
        private InputStream _is;
        private Query query = null;

        private QueryServiceTask(Query query) {
            this.query = query;
        }

        @Override
        protected ServiceResultObject doInBackground(Void... arg0) {
            mIsUpdate = true;
            ServiceResultObject serviceResultObject = new ServiceResultObject();
            int insertOrUpdateCount = 0;
            try {
                if (query.mPageInfo.mPageIndex == PageInfo.DEFAULT_PAGEINDEX) {
                    //开始刷新
                    onRefreshStart();
                    if (mIsFirstRefresh) {
                        mIsFirstRefresh = false;
                        final Cursor cursor = loadLocal(mContentResolver);
                        if (cursor != null) {
                            query.mPageInfo.computePageSize(cursor.getCount());
                            cursor.close();
                        }
                    }

                }
//				while (isNeedRequestAgain) {
                DebugUtils.logD(TAG, "start pageIndex " + query.mPageInfo.mPageIndex + " pageSize = " + query.mPageInfo.mPageSize);
                if (isCancelled()) {
                    return serviceResultObject;
                }
                _is = openConnection(buildPageQuery(query));

                if (isCancelled()) {
                    return serviceResultObject;
                }
                if (_is != null) {
                    DebugUtils.logD(TAG, "begin parseList....");
                    List<? extends InfoInterface> serviceInfoList = getServiceInfoList(_is, query.mPageInfo);
                    int newCount = serviceInfoList.size();
                    DebugUtils.logD(TAG, "find new date #count = " + newCount + " totalSize = " + query.mPageInfo.mTotalCount);
                    if (query.mPageInfo.mPageIndex == PageInfo.DEFAULT_PAGEINDEX) {
                        onRefreshEndV2(newCount, query.mPageInfo.mTotalCount);
                    }

                    if (newCount == 0) {
                        DebugUtils.logD(TAG, "no more date");
                        isNeedRequestAgain = false;
                        serviceResultObject.mStatusCode = REFRESH_RESULT_NO_MORE_DATE;
                        serviceResultObject.mStatusMessage = getActivity().getString(R.string.msg_nonew_for_receive);
                        return serviceResultObject;
                    }
                    if (query.mPageInfo.mTotalCount <= query.mPageInfo.mPageIndex * query.mPageInfo.mPageSize) {
                        DebugUtils.logD(TAG, "returned data count is less than that we requested, so not need to pull data again");
                        isNeedRequestAgain = false;
                    }
                    if (isCancelled()) {
                        return serviceResultObject;
                    }
                    DebugUtils.logD(TAG, "begin insert or update local database");
                    insertOrUpdateCount = savedIntoDatabase(mContentResolver, serviceInfoList);
                    if (query.mPageInfo.mTotalCount == insertOrUpdateCount) {
                        DebugUtils.logD(TAG, "returned data count is equal to insertOrUpdateCount, so not need to pull data again");
                        isNeedRequestAgain = false;
                    }
                    if (isNeedRequestAgain) {
                        query.mPageInfo.mPageIndex+=1;
                    }
                    serviceResultObject.mStatusMessage = String.valueOf(insertOrUpdateCount);
                } else {
                    DebugUtils.logD(TAG, "finish task due to openContectionLocked return null InputStream");
                    isNeedRequestAgain = false;
                    serviceResultObject.mStatusCode = REFRESH_RESULT_FAILED;
                    serviceResultObject.mStatusMessage = "openContectionLocked return null InputStream";
                }

            } catch (RefreshCustomException e) {
                e.printStackTrace();
                serviceResultObject.mStatusCode = REFRESH_RESULT_CUSTOM_OP;
                serviceResultObject.mStatusMessage = e.getMessage();
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
            onRefreshCanceled();
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
            case REFRESH_RESULT_CUSTOM_OP:
                ComApplication.getInstance().showMessage(statusMessage);
                break;
            case REFRESH_RESULT_OK:
                break;
        }
    }

    public void setRefreshCallback(RefreshCallback refreshCallback) {
        mRefreshCallback = refreshCallback;
    }

}
