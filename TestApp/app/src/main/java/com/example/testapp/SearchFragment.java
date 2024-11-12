package com.example.testapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment implements DataAdapter.OnItemClickListener {

    private EditText searchEditText;
    private RecyclerView searchRecyclerView;
    private Button searchButton;
    private TextView noResultsMessage;
    private DataAdapter searchAdapter;
    private ArrayList<DataModel> filteredList = new ArrayList<>();
    ;
    private int searchOffset = 0;
    private final int LIMIT = 20;
    private String trendingGifLink = API.BASE_TRENDING_URL + API.API_KEY + "&limit=" + LIMIT;
    private String searchGifLink = API.BASE_SEARCH_URL + API.API_KEY + "&limit=" + LIMIT + "&q=";
    private boolean isSearching = false;
    private String currentQuery = "";

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "SearchPrefs";
    private static final String LAST_SEARCH_RESULTS = "lastSearchResults";

    private AppDatabase db;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);
        db = AppDatabase.getInstance(getContext());
        searchEditText = view.findViewById(R.id.searchEditText);
        searchRecyclerView = view.findViewById(R.id.searchRecyclerView);
        searchButton = view.findViewById(R.id.searchButton);
        noResultsMessage = view.findViewById(R.id.noResultsMessage);

        loadTrendingGifs(trendingGifLink);
        initializeRecyclerView();
        sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastSearchKeyword = sharedPreferences.getString(LAST_SEARCH_RESULTS, "");
        searchEditText.setText(lastSearchKeyword);
        searchAdapter.setOnItemClickListener(this::onItemClick);
        searchButton.setOnClickListener(v -> performSearch());
        loadCachedSearchResults();
        return view;
    }

    private void initializeRecyclerView() {
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        searchRecyclerView.setLayoutManager(layoutManager);
        searchRecyclerView.setHasFixedSize(true);

        searchAdapter = new DataAdapter(getContext(), filteredList);
        searchRecyclerView.setAdapter(searchAdapter);

        searchRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!recyclerView.canScrollVertically(1)) {
                    loadMoreGifs();
                }
            }
        });

    }

    private void loadMoreGifs() {
        String url;
        if (isSearching) {
            url = searchGifLink + currentQuery + "&offset=" + searchOffset;
        } else {
            url = trendingGifLink + "&offset=" + searchOffset;
        }
        searchOffset += LIMIT;
        loadGifs(url);
    }

    private void loadGifs(String url) {
        JsonObjectRequest objectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @SuppressLint("NotifyDataSetChanged")
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            JSONArray dataArray = response.getJSONArray("data");

                            for (int i = 0; i < dataArray.length(); i++) {
                                JSONObject obj = dataArray.getJSONObject(i);
                                JSONObject imagesObj = obj.getJSONObject("images");
                                JSONObject downsizedMedium = imagesObj.getJSONObject("downsized_medium");
                                String imageUrl = downsizedMedium.getString("url");
                                int height = downsizedMedium.getInt("height");
                                String title = obj.getString("title");
                                filteredList.add(new DataModel(imageUrl, height, title));
                            }
                            searchAdapter.notifyDataSetChanged();
                            saveSearchResultsToDatabase(filteredList);
                            noResultsMessage.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
                        } catch (JSONException e) {
                            Toast.makeText(getContext(), "Error: " + e, Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
        MySingleton.getInstance(getContext()).addToRequestQueue(objectRequest);
    }

    private void loadTrendingGifs(String url) {
        JsonObjectRequest objectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @SuppressLint("NotifyDataSetChanged")
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            JSONArray dataArray = response.getJSONArray("data");

                            for (int i = 0; i < dataArray.length(); i++) {
                                JSONObject obj = dataArray.getJSONObject(i);
                                JSONObject imagesObj = obj.getJSONObject("images");
                                JSONObject downsizedMedium = imagesObj.getJSONObject("downsized_medium");
                                String imageUrl = downsizedMedium.getString("url");
                                int height = downsizedMedium.getInt("height");
                                String title = obj.getString("title");
                                filteredList.add(new DataModel(imageUrl, height, title));
                            }
                            for (DataModel dm : filteredList) {
                                System.out.println(dm);
                            }

                            searchAdapter.notifyDataSetChanged();
                        } catch (JSONException e) {
                            Toast.makeText(getContext(), "Error: " + e, Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
        MySingleton.getInstance(getContext()).addToRequestQueue(objectRequest);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void performSearch() {
        filteredList.clear();
        searchOffset = 0;
        currentQuery = searchEditText.getText().toString();
        isSearching = !currentQuery.isEmpty();
        if (isSearching) {
            sharedPreferences.edit().putString(LAST_SEARCH_RESULTS, currentQuery).apply();
            loadGifs(searchGifLink + currentQuery);
        } else {
            loadTrendingGifs(trendingGifLink);
        }
        noResultsMessage.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void saveSearchResultsToDatabase(ArrayList<DataModel> results) {
        // Convert DataModel to SearchResult and insert into the database
        List<SearchResult> searchResultsToCache = new ArrayList<>();
        for (DataModel data : results) {
            searchResultsToCache.add(new SearchResult(data.getImageUrl(), data.getHeight(), data.getTitle()));
        }
        new Thread(() -> db.searchResultDAO().insertListSearchResult(searchResultsToCache));
    }

    private void loadCachedSearchResults() {
        new Thread(() -> {
            List<SearchResult> cachedResults = db.searchResultDAO().getAll();
            if (cachedResults != null && !cachedResults.isEmpty()) {
                filteredList.clear();
                for (SearchResult result : cachedResults) {
                    filteredList.add(new DataModel(result.getImageUrl(), result.getHeight(), result.getTitle()));
                }
                getActivity().runOnUiThread(() -> {
                    searchAdapter.notifyDataSetChanged();
                    noResultsMessage.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } else {
                getActivity().runOnUiThread(() -> noResultsMessage.setVisibility(View.VISIBLE));
            }
        }).start();
    }


    @Override
    public void onItemClick(int pos) {
        Intent fullView = new Intent(getContext(), FullActivity.class);
        DataModel clickedItem = filteredList.get(pos);
        fullView.putExtra("imageFull", clickedItem);
        startActivity(fullView);
    }
}
