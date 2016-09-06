package com.bestjoy.app.swiperefreshlibrary;

/**
 * 刷新回调
 * Created by bestjoy on 16/5/25.
 */
public interface RefreshCallback {
    public void onRefresh(int dataCount, long dataTotal);
}
