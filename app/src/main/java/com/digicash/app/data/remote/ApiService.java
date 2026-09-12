package com.digicash.app.data.remote;

import com.digicash.app.data.remote.dto.SyncRequest;
import com.digicash.app.data.remote.dto.SyncResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Retrofit API contract for the DIGICASH backend. Only the sync endpoint
 * exists in the Android project - the backend implementation itself is
 * a separate deliverable.
 */
public interface ApiService {

    @POST("api/sync")
    Call<SyncResponse> syncTransactions(@Body SyncRequest request);
}
