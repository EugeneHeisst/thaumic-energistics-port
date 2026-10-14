package dev.eugheist.thaumicenergistics.me;

import net.minecraft.world.item.Rarity;

/** EnumEssentiaStorageTypes: bytes, essentia types and idle AE drain exactly as 1.1.3.0 (including its 16348-byte 16k cell). */
public enum EssentiaStorageType {
    TYPE_1K("1k", 1024, 12, Rarity.COMMON, .5),
    TYPE_4K("4k", 4096, 12, Rarity.UNCOMMON, 1),
    TYPE_16K("16k", 16348, 12, Rarity.UNCOMMON, 1.5),
    TYPE_64K("64k", 65536, 12, Rarity.RARE, 2),
    CREATIVE("creative", 0, 63, Rarity.EPIC, 0);

    public final String suffix;
    public final int capacity, maxStoredTypes;
    public final Rarity rarity;
    public final double idleAEPowerDrain;

    EssentiaStorageType(String suffix, int capacity, int maxStoredTypes, Rarity rarity, double idleAEPowerDrain) {
        this.suffix = suffix; this.capacity = capacity; this.maxStoredTypes = maxStoredTypes; this.rarity = rarity; this.idleAEPowerDrain = idleAEPowerDrain;
    }
    public boolean creative() { return this == CREATIVE; }
}
