package com.rentmate.app.network

import android.content.Context
import com.rentmate.app.BuildConfig
import com.rentmate.app.auth.TokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore = TokenStore(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            })
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideHouseholdsApi(retrofit: Retrofit): HouseholdsApi = retrofit.create(HouseholdsApi::class.java)

    @Provides
    @Singleton
    fun provideBillsApi(retrofit: Retrofit): BillsApi = retrofit.create(BillsApi::class.java)

    @Provides
    @Singleton
    fun provideChoresApi(retrofit: Retrofit): ChoresApi = retrofit.create(ChoresApi::class.java)

    @Provides
    @Singleton
    fun provideShoppingListApi(retrofit: Retrofit): ShoppingListApi = retrofit.create(ShoppingListApi::class.java)

    @Provides
    @Singleton
    fun provideMaintenanceApi(retrofit: Retrofit): MaintenanceApi = retrofit.create(MaintenanceApi::class.java)
}
