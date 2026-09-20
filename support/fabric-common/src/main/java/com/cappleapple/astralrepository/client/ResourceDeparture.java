package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.network.NetworkPackets;

/** Short, real-time fade and growth for resource sprites leaving an inscribed container face. */
final class ResourceDeparture {
    private static final double FADE_TICKS = 3;

    static float opacity(NetworkPackets.Visual packet, double ageTicks) {
        if (packet.departure() == null || packet.slot() < -4 || packet.slot() > -2) return 1;
        double progress = Math.clamp(ageTicks / FADE_TICKS, 0, 1);
        return (float) (progress * progress * (3 - 2 * progress));
    }

    static float scale(NetworkPackets.Visual packet, double ageTicks) {
        return 0.5F + 0.5F * opacity(packet, ageTicks);
    }

    private ResourceDeparture() {}
}
