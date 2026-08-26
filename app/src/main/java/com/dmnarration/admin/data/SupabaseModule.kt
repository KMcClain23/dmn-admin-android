package com.dmnarration.admin.data

import android.content.Context
import com.dmnarration.admin.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(@ApplicationContext context: Context): SupabaseClient {
        // Checked here rather than at configuration time so a fresh clone with
        // no local.properties still builds, and says something useful when it
        // runs instead of failing with an empty-URL parse error.
        check(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "Supabase credentials are missing. Add SUPABASE_URL and SUPABASE_ANON_KEY " +
                "to local.properties (git-ignored) and rebuild."
        }

        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
                sessionManager = EncryptedSessionManager(context)
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Postgrest)
        }
    }
}
