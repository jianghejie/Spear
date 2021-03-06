package me.xiaopan.android.spear.sample.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import org.apache.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;

import me.xiaoapn.android.spear.sample.R;
import me.xiaopan.android.gohttp.GoHttp;
import me.xiaopan.android.gohttp.HttpRequest;
import me.xiaopan.android.gohttp.HttpRequestFuture;
import me.xiaopan.android.gohttp.JsonHttpResponseHandler;
import me.xiaopan.android.inject.InjectContentView;
import me.xiaopan.android.inject.InjectExtra;
import me.xiaopan.android.inject.InjectView;
import me.xiaopan.android.inject.app.InjectFragment;
import me.xiaopan.android.spear.sample.activity.DetailActivity;
import me.xiaopan.android.spear.sample.adapter.StarImageAdapter;
import me.xiaopan.android.spear.sample.net.request.SearchImageRequest;
import me.xiaopan.android.spear.sample.net.request.StarImageRequest;
import me.xiaopan.android.spear.sample.widget.HintView;
import me.xiaopan.android.widget.PullRefreshLayout;

/**
 * 图片搜索Fragment
 */
@InjectContentView(R.layout.fragment_search)
public class SearchFragment extends InjectFragment implements StarImageAdapter.OnItemClickListener, PullRefreshLayout.OnRefreshListener {
    public static final String PARAM_OPTIONAL_STRING_SEARCH_KEYWORD = "PARAM_OPTIONAL_STRING_SEARCH_KEYWORD";

    @InjectView(R.id.refreshLayout_search) PullRefreshLayout pullRefreshLayout;
    @InjectView(R.id.recyclerView_search) private RecyclerView recyclerView;
    @InjectView(R.id.hintView_search) private HintView hintView;

    private SearchImageRequest searchImageRequest;
    private HttpRequestFuture refreshRequestFuture;
    private StarImageAdapter searchImageAdapter;
    private MyLoadMoreListener loadMoreListener;

    @InjectExtra(PARAM_OPTIONAL_STRING_SEARCH_KEYWORD) private String searchKeyword = "美女";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        searchImageRequest = new SearchImageRequest(searchKeyword);
        loadMoreListener = new MyLoadMoreListener();
        setHasOptionsMenu(true);
        if(getActivity() instanceof ActionBarActivity){
            ((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(searchKeyword);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        getActivity().getMenuInflater().inflate(R.menu.menu_search_view, menu);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.menu_searchView));
        searchView.setQueryHint(searchKeyword);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                s = s.trim();
                if("".equals(s)){
                    Toast.makeText(getActivity(), "搜索关键字不能为空", Toast.LENGTH_LONG).show();
                    return false;
                }

                getActivity().setTitle(s);
                Bundle bundle = new Bundle();
                bundle.putString(SearchFragment.PARAM_OPTIONAL_STRING_SEARCH_KEYWORD, s);
                SearchFragment searchFragment = new SearchFragment();
                searchFragment.setArguments(bundle);
                getFragmentManager()
                        .beginTransaction()
                        .setCustomAnimations(R.anim.window_push_enter, R.anim.window_push_exit)
                        .replace(R.id.frame_main_content, searchFragment)
                        .commit();

                ((InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(getActivity().getCurrentFocus()
                                        .getWindowToken(),
                                InputMethodManager.HIDE_NOT_ALWAYS);

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pullRefreshLayout.setOnRefreshListener(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setOnScrollListener(loadMoreListener);

        if (searchImageAdapter == null) {
            pullRefreshLayout.startRefresh();
        } else {
            recyclerView.setAdapter(searchImageAdapter);
            recyclerView.scheduleLayoutAnimation();
        }
    }

    @Override
    public void onDetach() {
        if (refreshRequestFuture != null && !refreshRequestFuture.isFinished()) {
            refreshRequestFuture.cancel(true);
        }

        super.onDetach();
    }

    @Override
    public void onRefresh() {
        if (refreshRequestFuture != null && !refreshRequestFuture.isFinished()) {
            return;
        }

        loadMoreListener.cancel();
        searchImageRequest.setStart(0);
        refreshRequestFuture = GoHttp.with(getActivity()).newRequest(searchImageRequest, new JsonHttpResponseHandler(SearchImageRequest.Response.class), new HttpRequest.Listener<SearchImageRequest.Response>() {
            @Override
            public void onStarted(HttpRequest httpRequest) {
                hintView.hidden();
            }

            @Override
            public void onCompleted(HttpRequest httpRequest, HttpResponse httpResponse, SearchImageRequest.Response responseObject, boolean b, boolean b2) {
                if (getActivity() == null) {
                    return;
                }

                List<StarImageRequest.Image> imageList = new ArrayList<StarImageRequest.Image>();
                for (SearchImageRequest.Image image : responseObject.getImages()) {
                    imageList.add(image);
                }

                recyclerView.setAdapter(searchImageAdapter = new StarImageAdapter(getActivity(), null, imageList, SearchFragment.this));
                recyclerView.scheduleLayoutAnimation();
                pullRefreshLayout.stopRefresh();
                loadMoreListener.reset();
                searchImageAdapter.setOnLoadMoreListener(loadMoreListener);
            }

            @Override
            public void onFailed(HttpRequest httpRequest, HttpResponse httpResponse, HttpRequest.Failure failure, boolean b, boolean b2) {
                if (getActivity() == null) {
                    return;
                }

                pullRefreshLayout.stopRefresh();
                if (searchImageAdapter == null) {
                    hintView.failure(failure, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pullRefreshLayout.startRefresh();
                        }
                    });
                } else {
                    Toast.makeText(getActivity(), "刷新失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCanceled(HttpRequest httpRequest) {

            }
        }).responseHandleCompletedAfterListener(new SearchImageRequest.ResponseHandler()).go();
    }

    @Override
    public void onItemClick(int position, StarImageRequest.Image image) {
        DetailActivity.launch(getActivity(), (ArrayList<String>) searchImageAdapter.getImageUrlList(), position);
    }

    private class MyLoadMoreListener extends RecyclerView.OnScrollListener implements StarImageAdapter.OnLoadMoreListener {
        private boolean end;
        private HttpRequestFuture loadMoreRequestFuture;

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public boolean isEnd() {
            return end;
        }

        public void reset(){
            end = false;
        }

        @Override
        public void onLoadMore() {
            if (refreshRequestFuture != null && !refreshRequestFuture.isFinished()) {
                return;
            }

            searchImageRequest.setStart(searchImageAdapter.getDataSize());
            loadMoreRequestFuture = GoHttp.with(getActivity()).newRequest(searchImageRequest, new JsonHttpResponseHandler(SearchImageRequest.Response.class), new HttpRequest.Listener<SearchImageRequest.Response>() {
                @Override
                public void onStarted(HttpRequest httpRequest) {

                }

                @Override
                public void onCompleted(HttpRequest httpRequest, HttpResponse httpResponse, SearchImageRequest.Response responseObject, boolean b, boolean b2) {
                    if (getActivity() == null) {
                        return;
                    }


                    List<StarImageRequest.Image> newImageList = null;
                    if (responseObject.getImages() != null) {
                        newImageList = new ArrayList<StarImageRequest.Image>();
                        for (SearchImageRequest.Image image : responseObject.getImages()) {
                            newImageList.add(image);
                        }
                    }

                    int count = searchImageAdapter.getItemCount();

                    if (newImageList != null && newImageList.size() > 0) {
                        searchImageAdapter.append(newImageList);
                        if (newImageList.size() < searchImageRequest.getSize()) {
                            end = true;
                            Toast.makeText(getActivity(), "新送达" + newImageList.size() + "个包裹，已全部送完！", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getActivity(), "新送达" + newImageList.size() + "个包裹", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        end = true;
                        Toast.makeText(getActivity(), "没有您的包裹了", Toast.LENGTH_SHORT).show();
                    }
                    searchImageAdapter.notifyItemInserted(count);
                }

                @Override
                public void onFailed(HttpRequest httpRequest, HttpResponse httpResponse, HttpRequest.Failure failure, boolean b, boolean b2) {
                    if (getActivity() == null) {
                        return;
                    }
                    searchImageAdapter.loadMoreFail();
                    Toast.makeText(getActivity(), "快递投递失败", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onCanceled(HttpRequest httpRequest) {

                }
            }).responseHandleCompletedAfterListener(new SearchImageRequest.ResponseHandler()).go();
        }

        public void cancel() {
            if (loadMoreRequestFuture != null && !loadMoreRequestFuture.isFinished()) {
                loadMoreRequestFuture.cancel(true);
            }
        }
    }
}
