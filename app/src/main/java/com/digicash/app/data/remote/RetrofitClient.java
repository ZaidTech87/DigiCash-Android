package com.digicash.app.data.remote;

import com.digicash.app.utils.Constants;
import com.google.gson.Gson;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Lazily-initialized singleton Retrofit client for the DIGICASH backend.
 * Timeouts are explicitly configured (rather than relying on OkHttp
 * defaults) so that a slow or unreachable server fails predictably
 * within Constants.HTTP_*_TIMEOUT_SECONDS instead of hanging indefinitely
 * inside a WorkManager job.
 */
public final class RetrofitClient {

    private static volatile ApiService apiServiceInstance;

    private RetrofitClient() {
        // Static accessor only.
    }

    public static ApiService getApiService() {
        if (apiServiceInstance == null) {
            synchronized (RetrofitClient.class) {
                if (apiServiceInstance == null) {
                    OkHttpClient okHttpClient = new OkHttpClient.Builder()
                            .connectTimeout(Constants.HTTP_CONNECT_TIMEOUT_SECONDS, Constants.HTTP_TIMEOUT_UNIT)
                            .readTimeout(Constants.HTTP_READ_TIMEOUT_SECONDS, Constants.HTTP_TIMEOUT_UNIT)
                            .writeTimeout(Constants.HTTP_WRITE_TIMEOUT_SECONDS, Constants.HTTP_TIMEOUT_UNIT)
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(Constants.API_BASE_URL)
                            .client(okHttpClient)
                            .addConverterFactory(GsonConverterFactory.create(new Gson()))
                            .build();

                    apiServiceInstance = retrofit.create(ApiService.class);
                }
            }
        }
        return apiServiceInstance;
    }
}
