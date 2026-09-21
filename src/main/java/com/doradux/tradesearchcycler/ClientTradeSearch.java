package com.doradux.tradesearchcycler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ClientTradeSearch {
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new ClientTradeSearch());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void open(ScreenEvent.Opening event) {
        // Reuse the server's menu and container ID. Respect another mod's custom screen.
        if (event.getNewScreen() != null && event.getNewScreen().getClass() == MerchantScreen.class
                && Minecraft.getInstance().player != null) {
            MerchantScreen original = (MerchantScreen) event.getNewScreen();
            event.setNewScreen(new SearchMerchantScreen(original.getMenu(),
                    Minecraft.getInstance().player.getInventory(), original.getTitle()));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void key(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof SearchMerchantScreen screen
                && screen.handleSearchKey(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void character(ScreenEvent.CharacterTyped.Pre event) {
        if (event.getScreen() instanceof SearchMerchantScreen screen
                && screen.handleSearchCharacter(event.getCodePoint(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }
}
