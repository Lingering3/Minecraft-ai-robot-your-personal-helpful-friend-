package io.github.zoyluo.aibot.item;

import io.github.zoyluo.aibot.AIBotMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registries;

public final class BlueprintItems {
    public static final Item BLUEPRINT = new BlueprintItem(new Item.Settings()
            .registryKey(RegistryKey.of(Registries.ITEM.getKey(),
                    Identifier.of(AIBotMod.MOD_ID, "blueprint")))
            .maxCount(1));

    private BlueprintItems() {
    }

    public static void register() {
        Registry.register(Registries.ITEM,
                Identifier.of(AIBotMod.MOD_ID, "blueprint"),
                BLUEPRINT);
    }
}
