package io.github.mgdx.potable.data

import io.github.mgdx.potable.data.local.PotableDatabase
import io.github.mgdx.potable.data.preferences.AppPreferences

/**
 * The application's [HistoryRepository]: Hub'Eau through [client], cached in
 * [database] for every unit looked at ([CachingHistoryRepository]).
 */
fun HistoryRepository(
    client: HubEauClient,
    database: PotableDatabase,
    preferences: AppPreferences,
): HistoryRepository =
    CachingHistoryRepository(RemoteHistoryRepository(client), database, preferences)
