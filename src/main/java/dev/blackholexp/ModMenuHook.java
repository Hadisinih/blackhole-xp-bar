package dev.blackholexp;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

// Dipanggil Mod Menu supaya muncul tombol pengaturan di daftar mod.
public class ModMenuHook implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BlackHoleXpClient.ConfigScreen::new;
    }
}
