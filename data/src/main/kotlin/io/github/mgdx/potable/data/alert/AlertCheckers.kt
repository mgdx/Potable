package io.github.mgdx.potable.data.alert

import io.github.mgdx.potable.data.CachingWaterRepository
import io.github.mgdx.potable.data.HubEauClient
import io.github.mgdx.potable.data.WaterRepository
import io.github.mgdx.potable.data.follow.FollowRepository

/**
 * The application's [AlertChecker] ([HubEauAlertChecker]): light requests
 * through [client], the last seen dates and the cache refresh through
 * [water], which must be the application's [CachingWaterRepository] (the
 * one that owns the table `synchro`).
 */
fun AlertChecker(
    client: HubEauClient,
    follows: FollowRepository,
    water: WaterRepository,
): AlertChecker {
    require(water is CachingWaterRepository) { "alerts need the caching water repository" }
    return HubEauAlertChecker(client, follows, water)
}
