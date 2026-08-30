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
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): EncryptedSessionManager =
        EncryptedSessionManager(context)

    @Provides
    @Singleton
    fun provideSupabaseClient(sessionStore: EncryptedSessionManager): SupabaseClient {
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
            // Finding 1: without this, an offline request sat for about 27
            // seconds before surfacing anything. Half a minute of spinner is a
            // bug in everything but name, and the board is small enough that
            // ten seconds is generous for a request that is going to succeed.
            requestTimeout = 10.seconds

            install(Auth) {
                sessionManager = sessionStore
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Postgrest)
            install(Functions)
        }
    }

    /**
     * The repositories are bound through their interfaces so the ViewModel can
     * be tested against fakes. Bug 6 was in the join between repository and
     * ViewModel, not in either half, and that join is only reachable in a test
     * if the repository can be substituted.
     */
    @Provides
    fun provideBoardRepository(impl: SupabaseBoardRepository): BoardRepository = impl

    @Provides
    fun provideStudioSettingsRepository(
        impl: SupabaseStudioSettingsRepository,
    ): StudioSettingsRepository = impl
}
