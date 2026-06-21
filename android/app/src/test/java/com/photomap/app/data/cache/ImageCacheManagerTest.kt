package com.photomap.app.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ImageCacheManagerTest {
    @Test
    fun signedUrlChangesDoNotChangeCacheKey() {
        val first = cloudImageCacheKey("asset-id", CloudImageVariant.THUMBNAIL)
        val second = cloudImageCacheKey("asset-id", CloudImageVariant.THUMBNAIL)

        assertEquals(first, second)
    }

    @Test
    fun assetAndVariantProduceDifferentCacheKeys() {
        assertNotEquals(
            cloudImageCacheKey("asset-one", CloudImageVariant.THUMBNAIL),
            cloudImageCacheKey("asset-two", CloudImageVariant.THUMBNAIL),
        )
        assertNotEquals(
            cloudImageCacheKey("asset-one", CloudImageVariant.THUMBNAIL),
            cloudImageCacheKey("asset-one", CloudImageVariant.PREVIEW),
        )
    }
}
