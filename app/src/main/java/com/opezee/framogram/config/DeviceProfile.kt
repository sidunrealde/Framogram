package com.opezee.framogram.config

/**
 * Per-device physical constants that cannot be queried from the platform.
 */
object DeviceProfile {
    /**
     * Front camera position relative to the screen center, in the display's NATURAL
     * orientation, meters. Default assumes a punch-hole/notch camera centered ~4 mm
     * above the top edge of the glass — measure and adjust for a specific kiosk device.
     */
    fun cameraOffsetNatural(naturalWidthM: Float, naturalHeightM: Float): Pair<Float, Float> =
        0f to (naturalHeightM / 2f + 0.004f)
}
