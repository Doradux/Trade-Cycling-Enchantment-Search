package com.doradux.tradesearchcycler;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

@Mod(TradeSearchCycler.MOD_ID)
public final class TradeSearchCycler {
    public static final String MOD_ID = "trade_search_cycler";

    public TradeSearchCycler() {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == Dist.CLIENT) {
            ClientTradeSearch.register();
        }
    }
}
