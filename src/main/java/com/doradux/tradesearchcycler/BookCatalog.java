package com.doradux.tradesearchcycler;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.nbt.Tag;
import net.minecraftforge.fml.ModList;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class BookCatalog {
    record Book(ResourceLocation id, Enchantment enchantment, int level, String label,
                String modName, String searchText, ItemStack icon) { }

    static List<Book> build() {
        List<Book> books = new ArrayList<>();
        BuiltInRegistries.ENCHANTMENT.forEach(enchantment -> {
            // Same predicate as VillagerTrades.EnchantBookForEmeralds in 1.20.1.
            // Do NOT use isDiscoverable: e.g. treasure enchantments may be sold.
            // Includes Enchantment Blacklist mixins and mod-specific enabled checks.
            if (!enchantment.isTradeable()) return;
            ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
            if (id == null) return;
            String modName = ModList.get().getModContainerById(id.getNamespace())
                    .map(mod -> mod.getModInfo().getDisplayName()).orElse(id.getNamespace());
            for (int level = enchantment.getMinLevel(); level <= enchantment.getMaxLevel(); level++) {
                ItemStack icon = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));
                String label = enchantment.getFullname(level).getString();
                books.add(new Book(id, enchantment, level, label, modName,
                        normalize(label + " " + id + " " + modName), icon));
            }
        });
        books.sort(Comparator.comparing(Book::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(book -> book.id().toString()).thenComparingInt(Book::level));
        return List.copyOf(books);
    }

    static boolean contains(MerchantOffers offers, Book book) {
        for (var offer : offers) {
            ItemStack item = offer.getResult();
            if (offer.isOutOfStock() || item.isEmpty()) continue;
            // StoredEnchantments also covers modded book items which do not extend EnchantedBookItem.
            // Reading the stored list directly avoids treating an enchanted sword as a book.
            if (item.getTag() == null) continue;
            var enchantments = item.getTag().getList("StoredEnchantments", Tag.TAG_COMPOUND);
            for (int i = 0; i < enchantments.size(); i++) {
                var entry = enchantments.getCompound(i);
                if (book.id().toString().equals(entry.getString("id")) && entry.getInt("lvl") == book.level()) {
                    return true;
                }
            }
        }
        return false;
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).trim();
    }
}
